package ie.shoonya.tracker.sync

/** Where an unsent event is in its journey to the sheet. */
enum class OutboxState {
    /** Committed locally, not yet sent. */
    PENDING,
    /** Handed to the transport; outcome unknown. The dangerous state. */
    IN_FLIGHT,
    /** Confirmed present in the sheet. */
    ACKED,
    /** Failed retryably; awaiting backoff. */
    FAILED,
    /** Failed permanently or too often. Skipped, never silently dropped. */
    POISON,
}

data class OutboxEntry(
    val seq: Long,
    val event: Event,
    val state: OutboxState = OutboxState.PENDING,
    val attempts: Int = 0,
    val lastError: String? = null,
)

/**
 * Decides what to send, and what to do with the answer.
 *
 * The problem this exists to solve: an append is sent, the network dies, and no
 * response arrives. Did it land? Google's Sheets API offers no upsert, no
 * conditional write, and no idempotency key, so a blind retry may append the
 * same expense twice — and a duplicated expense is a wrong total that the user
 * will not notice for weeks.
 *
 * The resolution is to make the *event itself* carry its identity. Because the
 * HLC is minted before the first attempt and reused on every retry, "did it
 * land?" becomes a question that can be answered by reading the log back.
 */
class Outbox(
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val batchLimit: Int = DEFAULT_BATCH_LIMIT,
) {
    private val entries = LinkedHashMap<Long, OutboxEntry>()
    private var nextSeq = 1L

    val size: Int get() = entries.size

    fun enqueue(event: Event): OutboxEntry {
        val entry = OutboxEntry(seq = nextSeq++, event = event)
        entries[entry.seq] = entry
        return entry
    }

    fun snapshot(): List<OutboxEntry> = entries.values.toList()

    fun byState(state: OutboxState): List<OutboxEntry> = entries.values.filter { it.state == state }

    /**
     * The next batch to send, in sequence order.
     *
     * FIFO within a device, and only one batch in flight at a time: concurrent
     * batches can land out of order, and while field application is
     * order-independent, keeping the order stable makes the log far easier to
     * reason about when something does go wrong.
     */
    fun nextBatch(): List<OutboxEntry> {
        if (entries.values.any { it.state == OutboxState.IN_FLIGHT }) return emptyList()
        return entries.values
            .filter { it.state == OutboxState.PENDING || it.state == OutboxState.FAILED }
            .take(batchLimit)
    }

    fun markInFlight(batch: List<OutboxEntry>) =
        batch.forEach { update(it.seq) { e -> e.copy(state = OutboxState.IN_FLIGHT) } }

    fun markAcked(batch: List<OutboxEntry>) =
        batch.forEach { update(it.seq) { e -> e.copy(state = OutboxState.ACKED, lastError = null) } }

    /**
     * A retryable failure. Attempts are counted so a permanently broken event
     * cannot spin forever, and once exhausted it becomes [OutboxState.POISON]
     * rather than being dropped — losing a financial record silently is worse
     * than surfacing an error.
     */
    fun markFailed(batch: List<OutboxEntry>, error: String) = batch.forEach {
        update(it.seq) { e ->
            val attempts = e.attempts + 1
            e.copy(
                state = if (attempts >= maxAttempts) OutboxState.POISON else OutboxState.FAILED,
                attempts = attempts,
                lastError = error,
            )
        }
    }

    fun markPoison(batch: List<OutboxEntry>, error: String) =
        batch.forEach { update(it.seq) { e -> e.copy(state = OutboxState.POISON, lastError = error) } }

    /**
     * Reconciles an in-flight batch against what is actually in the sheet, for
     * use after a crash or an ambiguous failure. Anything already present is
     * acked; anything absent returns to pending for a safe retry.
     */
    fun reconcile(confirmedHlcs: Set<String>) {
        entries.values.filter { it.state == OutboxState.IN_FLIGHT }.forEach { entry ->
            val landed = entry.event.hlc.encode() in confirmedHlcs
            update(entry.seq) { e ->
                if (landed) e.copy(state = OutboxState.ACKED)
                else e.copy(state = OutboxState.PENDING)
            }
        }
    }

    /** Drops acked entries once their events are known to be durable. */
    fun garbageCollect(): Int {
        val gone = entries.values.filter { it.state == OutboxState.ACKED }.map { it.seq }
        gone.forEach { entries.remove(it) }
        return gone.size
    }

    /**
     * Supersedes an earlier unsent edit to the same field. Typing an amount and
     * correcting it twice before any sync should send one event, not three.
     * Only unsent entries may be superseded — anything already in flight might
     * have landed.
     */
    fun supersede(event: Event): Int {
        val victims = entries.values.filter {
            it.state == OutboxState.PENDING &&
                it.event.entityId == event.entityId &&
                it.event.field == event.field &&
                it.event.hlc < event.hlc
        }.map { it.seq }
        victims.forEach { entries.remove(it) }
        enqueue(event)
        return victims.size
    }

    /** Backoff with full jitter. AWS measured full jitter as the best time-to-completion. */
    fun backoffMillis(attempts: Int, random: (Long) -> Long): Long {
        val exponential = BASE_BACKOFF_MILLIS shl minOf(attempts, 20)
        return random(minOf(exponential, MAX_BACKOFF_MILLIS))
    }

    private inline fun update(seq: Long, transform: (OutboxEntry) -> OutboxEntry) {
        entries[seq]?.let { entries[seq] = transform(it) }
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 8
        const val DEFAULT_BATCH_LIMIT = 500
        const val BASE_BACKOFF_MILLIS = 1_000L
        const val MAX_BACKOFF_MILLIS = 300_000L
    }
}
