package ie.shoonya.tracker.sync

import app.cash.sqldelight.db.SqlDriver
import ie.shoonya.tracker.db.VittDatabase

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
class EventStore(driver: SqlDriver) {

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

    fun allEvents(): List<Event> = events.selectAll().executeAsList().map { it.toEvent() }

    fun eventsSince(hlc: Hlc?): List<Event> =
        events.selectSince(hlc?.encode() ?: "").executeAsList().map { it.toEvent() }

    fun knownHlcs(): Set<String> = events.knownHlcs().executeAsList().toSet()

    fun eventCount(): Long = events.count().executeAsOne()

    /** The newest event this device has seen, local or remote. Null when empty. */
    fun maxHlc(): Hlc? = events.maxHlc().executeAsOne().max?.let { Hlc.decode(it) }

    fun fold(): Map<String, EventLog.Entity> = EventLog.fold(allEvents())

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

    fun markFailed(batch: List<Event>, error: String, maxAttempts: Int = 8) = db.transaction {
        batch.forEach { event ->
            val hlc = event.hlc.encode()
            val current = outbox.selectByState(OutboxState.IN_FLIGHT.name)
                .executeAsList().firstOrNull { it.hlc == hlc }
            val attempts = (current?.attempts ?: 0) + 1
            val next = if (attempts >= maxAttempts) OutboxState.POISON else OutboxState.FAILED
            outbox.markFailed(state = next.name, last_error = error, hlc = hlc)
        }
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
    }
}

data class ReconcileResult(val acked: Int, val requeued: Int)

private fun ie.shoonya.tracker.db.Event.toEvent() = Event(
    hlc = Hlc.decode(hlc),
    entity = entity,
    entityId = entity_id,
    field = field_,
    value = TaggedValue.decode(value_),
)
