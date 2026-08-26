package ie.shoonya.vitt.sync

/**
 * A Hybrid Logical Clock timestamp.
 *
 * Wall-clock time cannot order edits across devices: phone clocks drift, and a
 * device that was offline for a week reconnects carrying week-old timestamps
 * that would beat yesterday's edits from another device — silently resurrecting
 * stale amounts. A pure logical (Lamport) clock orders correctly but loses all
 * relation to real time, so you could no longer ask "what changed since Tuesday".
 *
 * An HLC gives both: causal ordering, and a physical component that stays within
 * a bounded distance of real time. Kulkarni et al., OPODIS 2014.
 *
 * Encoded fixed-width so that lexicographic string comparison *is* HLC
 * comparison — which lets the sheet be sorted by a plain text column:
 *
 *     2026-08-25T22:23:42.123Z-0000-a219e7a71cc18912
 *     |________ physical (l) ______| |c| |_ node id _|
 */
data class Hlc(
    val physicalMillis: Long,
    val counter: Int,
    val nodeId: String,
) : Comparable<Hlc> {

    init {
        require(counter in 0..MAX_COUNTER) { "counter $counter out of range" }
        require(nodeId.length == NODE_ID_LENGTH) {
            "nodeId must be $NODE_ID_LENGTH hex chars, got '${nodeId}'"
        }
    }

    /**
     * Ordering is (physical, counter, nodeId). The nodeId tiebreak is arbitrary
     * but must be *total* and *stable*: without it two devices could produce
     * timestamps that compare equal, and last-write-wins would then depend on
     * which row you happened to read first — meaning two devices could fold the
     * same log into different states.
     */
    override fun compareTo(other: Hlc): Int {
        physicalMillis.compareTo(other.physicalMillis).let { if (it != 0) return it }
        counter.compareTo(other.counter).let { if (it != 0) return it }
        return nodeId.compareTo(other.nodeId)
    }

    fun encode(): String = buildString {
        append(Iso8601.format(physicalMillis))
        append('-')
        append(counter.toHexPadded(4))
        append('-')
        append(nodeId)
    }

    override fun toString(): String = encode()

    companion object {
        const val MAX_COUNTER = 0xFFFF
        const val NODE_ID_LENGTH = 16
        const val ENCODED_LENGTH = 24 + 1 + 4 + 1 + NODE_ID_LENGTH

        fun decode(encoded: String): Hlc {
            require(encoded.length == ENCODED_LENGTH) {
                "expected $ENCODED_LENGTH chars, got ${encoded.length}: '$encoded'"
            }
            val iso = encoded.substring(0, 24)
            val counter = encoded.substring(25, 29).toInt(16)
            val node = encoded.substring(30)
            return Hlc(Iso8601.parse(iso), counter, node)
        }
    }
}

private fun Int.toHexPadded(width: Int): String =
    toString(16).padStart(width, '0')

/**
 * Advances an HLC. Not thread-safe by design: callers must hold whatever lock
 * guards their outbox, because the clock must only advance once a mutation has
 * actually been committed. Advancing first and then rolling back the
 * transaction leaves the clock ahead of the log, which is unrecoverable.
 */
class HlcClock(
    private val nodeId: String,
    private val maxDriftMillis: Long = DEFAULT_MAX_DRIFT_MILLIS,
    seed: Hlc? = null,
    private val now: () -> Long,
) {
    /**
     * Seeding matters more than it looks. Without it the clock restarts at epoch
     * zero on every launch and relies on the wall clock never moving backwards
     * across a process boundary — which it does, every time NTP corrects a fast
     * phone. An edit made before the correction then outranks the correction
     * itself, and the user's own fix is silently discarded on every device.
     */
    private var last: Hlc = seed ?: Hlc(0L, 0, nodeId)

    val current: Hlc get() = last

    /**
     * Issues a timestamp for a local event.
     *
     * Note there is deliberately no `+1` on the physical component — that is
     * what distinguishes a true HLC from a Lamport clock with a timestamp
     * attached, and it is what keeps the physical part close to real time.
     */
    fun issue(): Hlc {
        val physical = now()
        val previous = last
        var newPhysical = maxOf(previous.physicalMillis, physical)
        var newCounter = if (newPhysical == previous.physicalMillis) previous.counter + 1 else 0

        // Saturating the counter borrows a millisecond rather than throwing.
        // Throwing here would abort a bulk import mid-way — 65,536 events inside
        // one millisecond is reachable when now() is coarse — and losing the
        // import is far worse than a timestamp one millisecond early.
        if (newCounter > Hlc.MAX_COUNTER) {
            newPhysical += 1
            newCounter = 0
        }

        // Deliberately no drift check on the local path. A device whose own
        // clock jumped forward an hour would otherwise be unable to record
        // anything until real time caught up — it would be refusing its owner's
        // data to defend against its owner. Monotonicity is the guarantee here;
        // drift is policed on the remote path, where it can actually be someone
        // else's fault.
        return Hlc(newPhysical, newCounter, nodeId).also { last = it }
    }

    /**
     * Merges a timestamp observed from another device, returning the local clock's
     * new value. Must be called for every remote event seen, including ones that
     * lose their last-write-wins comparison — otherwise this device's clock lags
     * and its subsequent edits are ordered before edits it has already seen.
     */
    fun observe(remote: Hlc): Hlc {
        val physical = now()
        val previous = last
        val newPhysical = maxOf(previous.physicalMillis, remote.physicalMillis, physical)
        val newCounter = when {
            newPhysical == previous.physicalMillis && newPhysical == remote.physicalMillis ->
                maxOf(previous.counter, remote.counter) + 1
            newPhysical == previous.physicalMillis -> previous.counter + 1
            newPhysical == remote.physicalMillis -> remote.counter + 1
            else -> 0
        }
        checkDrift(newPhysical, physical)
        if (newCounter > Hlc.MAX_COUNTER) {
            return Hlc(newPhysical + 1, 0, nodeId).also { last = it }
        }
        return Hlc(newPhysical, newCounter, nodeId).also { last = it }
    }

    /**
     * Rejects a remote clock that has run too far ahead of local wall time. Without
     * this, one device with a badly wrong clock poisons the ordering for every
     * device that observes it, permanently — every later edit everywhere has to
     * sort after that bogus future timestamp.
     */
    private fun checkDrift(newPhysical: Long, wallClock: Long) {
        val drift = newPhysical - wallClock
        if (drift > maxDriftMillis) {
            throw ClockDriftException(driftMillis = drift, maxMillis = maxDriftMillis)
        }
    }

    companion object {
        const val DEFAULT_MAX_DRIFT_MILLIS: Long = 5 * 60 * 1000
    }
}

class ClockDriftException(val driftMillis: Long, val maxMillis: Long) : IllegalStateException(
    "clock drift ${driftMillis}ms exceeds maximum ${maxMillis}ms; " +
        "another device's clock is likely wrong"
)
