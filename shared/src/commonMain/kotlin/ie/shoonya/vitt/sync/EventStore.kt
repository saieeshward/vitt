package ie.shoonya.vitt.sync

import app.cash.sqldelight.db.SqlDriver
import ie.shoonya.vitt.db.VittDatabase

/**
 * Local persistence for the event log and the outbox.
 *
 * The single rule this type exists to enforce: **appending an event and
 * enqueuing it for sync happen in one transaction.** If they could come apart,
 * a crash between them would either lose a change the user saw accepted, or
 * queue a sync for an event that does not exist locally. Both are corruption of
 * financial data, and neither is detectable after the fact.
 *
 * The clock is advanced by the caller only after [append] returns, for the same
 * reason: a rolled-back transaction must not leave the clock ahead of the log.
 */
class EventStore(
    driver: SqlDriver,
    /**
     * The device's clock. Production always supplies one via [open], which seeds
     * it from persisted state; tests that mint their own HLCs may omit it.
     */
    private val clock: HlcClock? = null,
) {

    private val db = VittDatabase(driver)
    private val events = db.eventQueries
    private val outbox = db.outboxQueries
    private val state = db.syncStateQueries

    /**
     * Records a locally-made change and queues it for the sheet.
     *
     * Returns false if this HLC was already present, which makes replay safe:
     * the same event applied twice is a no-op rather than a duplicate row.
     */
    fun append(event: Event, nowMillis: Long): Boolean = db.transactionWithResult {
        events.insert(
            hlc = event.hlc.encode(),
            node_id = event.hlc.nodeId,
            entity = event.entity,
            entity_id = event.entityId,
            field_ = event.field,
            value_ = event.value.encode(),
        )
        val inserted = events.changes().executeAsOne() > 0
        if (inserted) {
            outbox.enqueue(hlc = event.hlc.encode(), created_at = nowMillis)
        }
        // Persisted inside the same transaction as the event, so a crash can
        // never leave a clock that has forgotten a timestamp it already issued.
        rememberClock(event.hlc)
        inserted
    }

    /**
     * Records events that arrived from the sheet.
     *
     * These are deliberately *not* enqueued: they came from the sheet, so
     * sending them back would be an echo that grows the log without adding
     * information.
     */
    fun appendRemote(incoming: List<Event>): Int = db.transactionWithResult {
        var new = 0
        incoming.forEach { event ->
            // Advancing the local clock past every remote timestamp is what
            // makes causality hold. Without it this device's next edit can carry
            // a timestamp older than a change it has already seen, and the fold
            // discards the user's correction on every device — silently, after
            // the UI showed it accepted.
            observeSafely(event.hlc)
            events.insert(
                hlc = event.hlc.encode(),
                node_id = event.hlc.nodeId,
                entity = event.entity,
                entity_id = event.entityId,
                field_ = event.field,
                value_ = event.value.encode(),
            )
            if (events.changes().executeAsOne() > 0) new++
        }
        new
    }

    /**
     * Merges a remote timestamp into the local clock, tolerating a bad one.
     *
     * A device with a wildly wrong clock must not be able to stop every other
     * device from syncing, so its event is still stored — the fold orders by the
     * timestamp itself — but it is not allowed to drag this clock into the
     * future with it.
     */
    private fun observeSafely(remote: Hlc) {
        val c = clock ?: return
        val merged = runCatching { c.observe(remote) }.getOrNull() ?: return
        rememberClock(merged)
    }

    /** Records the high-water mark so the clock survives a restart. */
    private fun rememberClock(hlc: Hlc) {
        val stored = get(KEY_LAST_HLC)
        if (stored == null || hlc.encode() > stored) {
            state.put(KEY_LAST_HLC, hlc.encode())
        }
    }

    /**
     * Replaces any unsent edit to the same field with a newer one.
     *
     * Correcting an amount three times before a sync should send one event, not
     * three. Only PENDING entries are collapsed — an in-flight entry may already
     * have reached the sheet, and dropping it would lose a committed change.
     */
    fun appendSuperseding(event: Event, nowMillis: Long): Int = db.transactionWithResult {
        val victims = outbox.supersededBy(
            entity_id = event.entityId,
            field_ = event.field,
            hlc = event.hlc.encode(),
        ).executeAsList()
        victims.forEach { outbox.delete(it) }

        events.insert(
            hlc = event.hlc.encode(),
            node_id = event.hlc.nodeId,
            entity = event.entity,
            entity_id = event.entityId,
            field_ = event.field,
            value_ = event.value.encode(),
        )
        if (events.changes().executeAsOne() > 0) {
            outbox.enqueue(hlc = event.hlc.encode(), created_at = nowMillis)
        }
        victims.size
    }

    /**
     * Mints a timestamp for a local change.
     *
     * Callers go through the store rather than holding a clock of their own, so
     * that issuing and persisting cannot come apart.
     */
    fun issue(): Hlc = requireNotNull(clock) { "this EventStore was opened without a clock" }.issue()

    fun allEvents(): List<Event> = events.selectAll().executeAsList().map { it.toEvent() }

    fun eventsSince(hlc: Hlc?): List<Event> =
        events.selectSince(hlc?.encode() ?: "").executeAsList().map { it.toEvent() }

    fun knownHlcs(): Set<String> = events.knownHlcs().executeAsList().toSet()

    fun eventCount(): Long = events.count().executeAsOne()

    /** The newest event this device has seen, local or remote. Null when empty. */
    fun maxHlc(): Hlc? = events.maxHlc().executeAsOne().max?.let { Hlc.decode(it) }

    fun fold(): Map<EventLog.EntityKey, EventLog.Entity> = EventLog.fold(allEvents())

    // ---- outbox --------------------------------------------------------------

    /** Empty while a batch is in flight: one batch at a time keeps ordering simple. */
    fun nextBatch(limit: Long = 500): List<Event> {
        if (outbox.inFlightCount().executeAsOne() > 0) return emptyList()
        return outbox.nextBatch(limit).executeAsList().map {
            Event(
                hlc = Hlc.decode(it.hlc),
                entity = it.entity,
                entityId = it.entity_id,
                field = it.field_,
                value = TaggedValue.decode(it.value_),
            )
        }
    }

    fun markInFlight(batch: List<Event>) = db.transaction {
        batch.forEach { outbox.markState(OutboxState.IN_FLIGHT.name, it.hlc.encode()) }
    }

    fun markAcked(batch: List<Event>) = db.transaction {
        batch.forEach { outbox.markState(OutboxState.ACKED.name, it.hlc.encode()) }
    }

    /**
     * A retryable failure for the whole batch.
     *
     * Attempts are read per entry rather than by scanning the in-flight list —
     * the previous version ran a full scan inside the loop, and counted from
     * zero for any entry reconcile had already moved off IN_FLIGHT, so such an
     * entry could never reach [OutboxState.POISON] however often it failed.
     */
    fun markFailed(batch: List<Event>, error: String, maxAttempts: Int = 8) = db.transaction {
        val byHlc = outbox.selectAll().executeAsList().associateBy { it.hlc }
        batch.forEach { event ->
            val hlc = event.hlc.encode()
            val attempts = (byHlc[hlc]?.attempts ?: 0) + 1
            val next = if (attempts >= maxAttempts) OutboxState.POISON else OutboxState.FAILED
            outbox.markFailed(state = next.name, last_error = error, hlc = hlc)
        }
    }

    /**
     * Poisons only the events that are actually to blame and returns the rest to
     * the queue.
     *
     * The distinction matters more than it sounds: a 400 fails the whole append,
     * so without this one bad row takes up to 500 good financial records with
     * it, unrecoverably — `nextBatch` never selects POISON again.
     */
    fun poisonOnly(offenders: List<Event>, rest: List<Event>, error: String) = db.transaction {
        offenders.forEach { outbox.markFailed(OutboxState.POISON.name, error, it.hlc.encode()) }
        rest.forEach { outbox.markState(OutboxState.PENDING.name, it.hlc.encode()) }
    }

    /**
     * Returns poisoned entries to the queue with their attempt count cleared.
     *
     * Poison is not a grave. A row rejected because a cell was too long becomes
     * sendable once the user shortens the note, and there has to be a way back.
     */
    fun retryPoisoned(): Int = db.transactionWithResult {
        val poisoned = outbox.selectByState(OutboxState.POISON.name).executeAsList()
        poisoned.forEach { outbox.retry(it.hlc) }
        poisoned.size
    }

    /**
     * Resolves in-flight entries against what the sheet actually contains.
     *
     * This is the recovery path for the case the whole design exists to survive:
     * the write reached the server but the response never came back. Anything
     * found in the sheet is acked; anything absent returns to pending, which is
     * safe precisely because the HLC was minted before the first attempt.
     */
    fun reconcile(confirmedHlcs: Set<String>): ReconcileResult = db.transactionWithResult {
        var acked = 0
        var requeued = 0
        outbox.reconcileInFlight().executeAsList().forEach { hlc ->
            if (hlc in confirmedHlcs) {
                outbox.markState(OutboxState.ACKED.name, hlc); acked++
            } else {
                outbox.markState(OutboxState.PENDING.name, hlc); requeued++
            }
        }
        ReconcileResult(acked = acked, requeued = requeued)
    }

    fun garbageCollect() { outbox.deleteAcked() }

    fun outboxCounts(): Map<OutboxState, Long> =
        outbox.countByState().executeAsList().associate { OutboxState.valueOf(it.state) to it.n }

    fun pendingCount(): Long = outboxCounts()
        .filterKeys { it != OutboxState.ACKED && it != OutboxState.POISON }
        .values.sum()

    /** Entries that failed permanently. Surfaced to the user; never silently dropped. */
    fun poisoned(): List<String> =
        outbox.selectByState(OutboxState.POISON.name).executeAsList().map { it.hlc }

    // ---- sync bookkeeping ----------------------------------------------------

    fun put(key: String, value: String) = state.put(key, value)

    fun get(key: String): String? = state.get(key).executeAsOneOrNull()

    companion object {
        const val KEY_NODE_ID = "node_id"
        const val KEY_SPREADSHEET_ID = "spreadsheet_id"
        const val KEY_DRIVE_VERSION = "drive_version"
        const val KEY_LAST_HLC = "last_hlc"

        /**
         * Opens a store with a clock seeded from what this device already
         * issued or saw.
         *
         * The seed is the later of the persisted high-water mark and the newest
         * event in the log, so a store whose `sync_state` was lost still cannot
         * reissue a timestamp it has already used.
         */
        fun open(driver: SqlDriver, nodeId: String, now: () -> Long): EventStore {
            val bootstrap = EventStore(driver)
            val persisted = bootstrap.get(KEY_LAST_HLC)?.let { runCatching { Hlc.decode(it) }.getOrNull() }
            val newest = bootstrap.maxHlc()
            val seed = listOfNotNull(persisted, newest).maxOrNull()
            return EventStore(driver, HlcClock(nodeId = nodeId, seed = seed, now = now))
        }
    }
}

data class ReconcileResult(val acked: Int, val requeued: Int)

private fun ie.shoonya.vitt.db.Event.toEvent() = Event(
    hlc = Hlc.decode(hlc),
    entity = entity,
    entityId = entity_id,
    field = field_,
    value = TaggedValue.decode(value_),
)
