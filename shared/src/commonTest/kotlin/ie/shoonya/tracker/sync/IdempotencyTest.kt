package ie.shoonya.tracker.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spike 3: can the app write to a Google Sheet without ever producing a
 * duplicate row, given that the API has no upsert and no compare-and-swap?
 *
 * Duplicates are the failure mode here, not corruption. A duplicated expense is
 * a quietly wrong total, which is worse than a visible error.
 */
class IdempotencyTest {

    private val nodeA = "a219e7a71cc18912"
    private val nodeB = "b0f1c2d3e4f50617"

    private fun clockAt(node: String, start: Long): HlcClock {
        var t = start
        return HlcClock(node, now = { t++ })
    }

    private fun expense(clock: HlcClock, id: String, minor: Long) =
        Event(clock.issue(), "transaction", id, "amount", TaggedValue.Num(minor))

    @Test
    fun `retry after a lost response does not duplicate the row`() {
        val sheet = FakeSheet()
        val outbox = Outbox()
        val clock = clockAt(nodeA, 1_000)

        outbox.enqueue(expense(clock, "txn-1", -1250))

        // Attempt 1: the server commits, the response is lost.
        val batch = outbox.nextBatch()
        outbox.markInFlight(batch)
        runCatching { sheet.appendCrashingAfterWrite(batch.map { it.event.toRow() }) }
        assertEquals(1, sheet.rowCount, "the server did commit")

        // The client has no idea. It reconciles against what is actually there
        // rather than blindly retrying.
        outbox.reconcile(sheet.knownHlcs())

        assertEquals(emptyList(), outbox.nextBatch(), "nothing left to send")
        assertEquals(1, sheet.rowCount)
        assertEquals(emptyList(), sheet.duplicateHlcs())
    }

    @Test
    fun `a genuine pre-write failure is retried and lands exactly once`() {
        val sheet = FakeSheet()
        val outbox = Outbox()
        val clock = clockAt(nodeA, 1_000)
        outbox.enqueue(expense(clock, "txn-1", -1250))

        val first = outbox.nextBatch()
        outbox.markInFlight(first)
        runCatching { sheet.appendFailingBeforeWrite() }
        outbox.reconcile(sheet.knownHlcs())   // nothing landed -> back to pending

        val retry = outbox.nextBatch()
        assertEquals(1, retry.size, "must retry, since nothing was written")
        outbox.markInFlight(retry)
        sheet.append(retry.map { it.event.toRow() })
        outbox.markAcked(retry)

        assertEquals(1, sheet.rowCount)
        assertEquals(emptyList(), sheet.duplicateHlcs())
    }

    @Test
    fun `ten consecutive crash-and-recover cycles produce no duplicates`() {
        // The acceptance criterion from the plan: kill it mid-write, ten times.
        val sheet = FakeSheet()
        val outbox = Outbox()
        val clock = clockAt(nodeA, 1_000)

        repeat(10) { i ->
            outbox.enqueue(expense(clock, "txn-$i", -100L * (i + 1)))
            val batch = outbox.nextBatch()
            outbox.markInFlight(batch)
            runCatching { sheet.appendCrashingAfterWrite(batch.map { it.event.toRow() }) }
            // Simulates a cold start: the outbox survived, the response did not.
            outbox.reconcile(sheet.knownHlcs())
            outbox.garbageCollect()
        }

        assertEquals(10, sheet.rowCount)
        assertEquals(emptyList(), sheet.duplicateHlcs())
        assertEquals(0, outbox.size, "everything reconciled and collected")
    }

    @Test
    fun `replaying the whole log is idempotent`() {
        // Folding the same events twice must not change the outcome, or a resync
        // would produce different balances than the device already showed.
        val clock = clockAt(nodeA, 1_000)
        val events = listOf(
            expense(clock, "txn-1", -1250),
            Event(clock.issue(), "transaction", "txn-1", "merchant", TaggedValue.Str("Tesco")),
        )
        val once = EventLog.fold(events)
        val twice = EventLog.fold(events + events)
        assertEquals(once, twice)
    }

    @Test
    fun `identical hlc seen twice is deduplicated, but two real coffees are not`() {
        val clock = clockAt(nodeA, 1_000)
        val e = expense(clock, "txn-1", -350)
        // The same event twice in one batch is still one event.
        assertEquals(1, EventLog.newEvents(listOf(e, e), emptySet()).size)
        // Already known -> filtered.
        assertEquals(0, EventLog.newEvents(listOf(e), setOf(e.hlc.encode())).size)

        // Two separate EUR 3.50 coffees on the same day are different events and
        // must both survive: dedup is by HLC, never by content.
        val c1 = expense(clock, "txn-a", -350)
        val c2 = expense(clock, "txn-b", -350)
        assertEquals(2, EventLog.newEvents(listOf(c1, c2), emptySet()).size)
    }

    @Test
    fun `two devices editing different fields both win`() {
        val a = clockAt(nodeA, 1_000)
        val b = clockAt(nodeB, 1_000)
        val events = listOf(
            Event(a.issue(), "transaction", "txn-1", "amount", TaggedValue.Num(-1250)),
            Event(b.issue(), "transaction", "txn-1", "category", TaggedValue.Str("Groceries")),
        )
        val folded = EventLog.fold(events).getValue("txn-1")
        assertEquals(TaggedValue.Num(-1250), folded.fields["amount"])
        assertEquals(TaggedValue.Str("Groceries"), folded.fields["category"])
    }

    @Test
    fun `same field concurrent edit resolves to the later hlc, both orders`() {
        val a = HlcClock(nodeA, now = { 5_000 })
        val b = HlcClock(nodeB, now = { 6_000 })
        val early = Event(a.issue(), "transaction", "txn-1", "amount", TaggedValue.Num(-1000))
        val late = Event(b.issue(), "transaction", "txn-1", "amount", TaggedValue.Num(-2000))

        // Convergence: arrival order must not affect the outcome.
        val forward = EventLog.fold(listOf(early, late)).getValue("txn-1")
        val reverse = EventLog.fold(listOf(late, early)).getValue("txn-1")
        assertEquals(TaggedValue.Num(-2000), forward.fields["amount"])
        assertEquals(forward, reverse)
    }

    @Test
    fun `superseding collapses unsent edits to the same field`() {
        val outbox = Outbox()
        val clock = clockAt(nodeA, 1_000)
        outbox.enqueue(expense(clock, "txn-1", -1000))
        outbox.supersede(expense(clock, "txn-1", -1200))
        val removed = outbox.supersede(expense(clock, "txn-1", -1250))

        assertTrue(removed >= 1)
        assertEquals(1, outbox.size, "three corrections, one event to send")
        assertEquals(
            TaggedValue.Num(-1250),
            outbox.snapshot().single().event.value,
        )
    }

    @Test
    fun `an in-flight edit is never superseded`() {
        // It might already have landed; removing it would lose a committed change.
        val outbox = Outbox()
        val clock = clockAt(nodeA, 1_000)
        outbox.enqueue(expense(clock, "txn-1", -1000))
        outbox.markInFlight(outbox.nextBatch())
        outbox.supersede(expense(clock, "txn-1", -1250))
        assertEquals(2, outbox.size)
    }

    @Test
    fun `a permanently failing event becomes poison and stops blocking the queue`() {
        val outbox = Outbox(maxAttempts = 3)
        val clock = clockAt(nodeA, 1_000)
        outbox.enqueue(expense(clock, "txn-bad", -1))

        repeat(3) {
            val batch = outbox.nextBatch()
            outbox.markInFlight(batch)
            outbox.markFailed(batch, "400 invalid")
        }
        assertEquals(1, outbox.byState(OutboxState.POISON).size)

        // A later event must still be sendable: head-of-line blocking on a
        // financial queue would stall every subsequent transaction.
        outbox.enqueue(expense(clock, "txn-good", -500))
        assertEquals(1, outbox.nextBatch().size)
    }

    @Test
    fun `backoff is bounded and jittered`() {
        val outbox = Outbox()
        // Full jitter draws from [0, cap), so it must never exceed the ceiling.
        assertEquals(0L, outbox.backoffMillis(0) { 0L })
        assertTrue(outbox.backoffMillis(50) { it } <= Outbox.MAX_BACKOFF_MILLIS)
        assertEquals(1_000L, outbox.backoffMillis(0) { it })
        assertEquals(2_000L, outbox.backoffMillis(1) { it })
    }

    @Test
    fun `tombstones survive folding`() {
        val clock = clockAt(nodeA, 1_000)
        val events = listOf(
            expense(clock, "txn-1", -1250),
            Event(clock.issue(), "transaction", "txn-1", EventLog.TOMBSTONE_FIELD, TaggedValue.Bool(true)),
        )
        val folded = EventLog.fold(events).getValue("txn-1")
        assertTrue(folded.deleted)
        assertTrue(EventLog.TOMBSTONE_FIELD !in folded.fields, "tombstone is not a user field")
    }

    @Test
    fun `rows round trip through the sheet representation`() {
        val clock = clockAt(nodeA, 1_000)
        val original = expense(clock, "txn-1", -1250)
        assertEquals(Event.COLUMNS, original.toRow().size)
        assertEquals(original, Event.fromRow(original.toRow()))
    }
}
