package ie.shoonya.vitt.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The same guarantees IdempotencyTest proves in memory, now against a real
 * SQLite database with real transactions.
 *
 * This matters because the in-memory version could not exercise the one thing
 * most likely to corrupt a ledger: a crash landing between the local write and
 * the outbox enqueue.
 */
class EventStoreTest {

    private val nodeA = "a219e7a71cc18912"
    private val nodeB = "b0f1c2d3e4f50617"

    private fun store() = EventStore(testDriver())

    private fun clockAt(node: String, start: Long): HlcClock {
        var t = start
        return HlcClock(node, now = { t++ })
    }

    private fun amount(clock: HlcClock, id: String, minor: Long) =
        Event(clock.issue(), "transaction", id, "amount", TaggedValue.Num(minor))

    @Test
    fun `append writes the event and queues it in one step`() {
        val s = store()
        val c = clockAt(nodeA, 1_000)
        assertTrue(s.append(amount(c, "txn-1", -1250), nowMillis = 1))
        assertEquals(1, s.eventCount())
        assertEquals(1, s.pendingCount())
        assertEquals(1, s.nextBatch().size)
    }

    @Test
    fun `appending the same event twice is a no-op`() {
        val s = store()
        val c = clockAt(nodeA, 1_000)
        val e = amount(c, "txn-1", -1250)
        assertTrue(s.append(e, 1))
        assertFalse(s.append(e, 2), "a replayed event must not be queued again")
        assertEquals(1, s.eventCount())
        assertEquals(1, s.pendingCount())
    }

    @Test
    fun `remote events are stored but never echoed back`() {
        val s = store()
        val remote = clockAt(nodeB, 5_000)
        val new = s.appendRemote(listOf(amount(remote, "txn-9", -400)))
        assertEquals(1, new)
        assertEquals(1, s.eventCount())
        assertEquals(0, s.pendingCount(), "events from the sheet must not be sent back to it")
    }

    @Test
    fun `crash between write and response leaves exactly one row`() {
        val s = store()
        val c = clockAt(nodeA, 1_000)
        val sheet = FakeSheet()

        s.append(amount(c, "txn-1", -1250), 1)
        val batch = s.nextBatch()
        s.markInFlight(batch)
        runCatching { sheet.appendCrashingAfterWrite(batch.map { it.toRow() }) }

        // The device restarts knowing only that a batch was in flight.
        val result = s.reconcile(sheet.knownHlcs())
        assertEquals(1, result.acked)
        assertEquals(0, result.requeued)
        assertEquals(1, sheet.rowCount)
        assertEquals(emptyList(), sheet.duplicateHlcs())
    }

    @Test
    fun `failure before the write is safely retried`() {
        val s = store()
        val c = clockAt(nodeA, 1_000)
        val sheet = FakeSheet()

        s.append(amount(c, "txn-1", -1250), 1)
        val batch = s.nextBatch()
        s.markInFlight(batch)
        runCatching { sheet.appendFailingBeforeWrite() }

        val result = s.reconcile(sheet.knownHlcs())
        assertEquals(1, result.requeued, "nothing landed, so it must go back in the queue")

        val retry = s.nextBatch()
        assertEquals(1, retry.size)
        sheet.append(retry.map { it.toRow() })
        s.markAcked(retry)

        assertEquals(1, sheet.rowCount)
        assertEquals(emptyList(), sheet.duplicateHlcs())
    }

    @Test
    fun `ten crash cycles against a real database produce no duplicates`() {
        val s = store()
        val c = clockAt(nodeA, 1_000)
        val sheet = FakeSheet()

        repeat(10) { i ->
            s.append(amount(c, "txn-$i", -100L * (i + 1)), i.toLong())
            val batch = s.nextBatch()
            s.markInFlight(batch)
            runCatching { sheet.appendCrashingAfterWrite(batch.map { it.toRow() }) }
            s.reconcile(sheet.knownHlcs())
            s.garbageCollect()
        }

        assertEquals(10, sheet.rowCount)
        assertEquals(emptyList(), sheet.duplicateHlcs())
        assertEquals(0, s.pendingCount())
        assertEquals(10, s.eventCount())
    }

    @Test
    fun `only one batch is in flight at a time`() {
        val s = store()
        val c = clockAt(nodeA, 1_000)
        s.append(amount(c, "txn-1", -100), 1)
        s.markInFlight(s.nextBatch())
        s.append(amount(c, "txn-2", -200), 2)
        assertEquals(emptyList(), s.nextBatch(), "must not overlap batches")
    }

    @Test
    fun `superseding collapses unsent corrections to one event`() {
        val s = store()
        val c = clockAt(nodeA, 1_000)
        s.append(amount(c, "txn-1", -1000), 1)
        s.appendSuperseding(amount(c, "txn-1", -1200), 2)
        val removed = s.appendSuperseding(amount(c, "txn-1", -1250), 3)

        assertTrue(removed >= 1)
        assertEquals(1, s.pendingCount(), "three corrections, one thing to send")
        // The superseded events remain in the log; only the queue is collapsed.
        assertEquals(3, s.eventCount())
        assertEquals(TaggedValue.Num(-1250), s.nextBatch().single().value)
    }

    @Test
    fun `an in-flight edit is never superseded`() {
        val s = store()
        val c = clockAt(nodeA, 1_000)
        s.append(amount(c, "txn-1", -1000), 1)
        s.markInFlight(s.nextBatch())
        val removed = s.appendSuperseding(amount(c, "txn-1", -1250), 2)
        assertEquals(0, removed, "it may already have reached the sheet")
    }

    @Test
    fun `repeated failures become poison and stop blocking the queue`() {
        val s = store()
        val c = clockAt(nodeA, 1_000)
        s.append(amount(c, "txn-bad", -1), 1)

        repeat(8) {
            val batch = s.nextBatch()
            if (batch.isNotEmpty()) {
                s.markInFlight(batch)
                s.markFailed(batch, "400 invalid", maxAttempts = 8)
            }
        }
        assertEquals(1, s.poisoned().size)

        s.append(amount(c, "txn-good", -500), 2)
        assertEquals(1, s.nextBatch().size, "a poisoned entry must not stall later writes")
    }

    @Test
    fun `folding the stored log gives current state`() {
        val s = store()
        val c = clockAt(nodeA, 1_000)
        s.append(amount(c, "txn-1", -1000), 1)
        s.append(amount(c, "txn-1", -1250), 2)
        s.append(Event(c.issue(), "transaction", "txn-1", "merchant", TaggedValue.Str("Tesco")), 3)

        val folded = s.fold().getValue(EventLog.EntityKey("transaction", "txn-1"))
        assertEquals(TaggedValue.Num(-1250), folded.fields["amount"], "latest write wins")
        assertEquals(TaggedValue.Str("Tesco"), folded.fields["merchant"])
        assertFalse(folded.deleted)
    }

    @Test
    fun `two devices converge on the same state through the store`() {
        val a = EventStore(testDriver())
        val b = EventStore(testDriver())
        val ca = HlcClock(nodeA, now = { 5_000 })
        val cb = HlcClock(nodeB, now = { 6_000 })

        val fromA = Event(ca.issue(), "transaction", "txn-1", "amount", TaggedValue.Num(-1000))
        val fromB = Event(cb.issue(), "transaction", "txn-1", "amount", TaggedValue.Num(-2000))

        // Each device sees both events, in opposite orders.
        a.append(fromA, 1); a.appendRemote(listOf(fromB))
        b.append(fromB, 1); b.appendRemote(listOf(fromA))

        assertEquals(a.fold(), b.fold(), "order of arrival must not change the outcome")
        assertEquals(TaggedValue.Num(-2000), a.fold().getValue(EventLog.EntityKey("transaction", "txn-1")).fields["amount"])
    }

    @Test
    fun `sync bookkeeping round trips`() {
        val s = store()
        assertNull(s.get(EventStore.KEY_SPREADSHEET_ID))
        s.put(EventStore.KEY_SPREADSHEET_ID, "1AbC")
        assertEquals("1AbC", s.get(EventStore.KEY_SPREADSHEET_ID))
        s.put(EventStore.KEY_SPREADSHEET_ID, "2XyZ")
        assertEquals("2XyZ", s.get(EventStore.KEY_SPREADSHEET_ID))
    }

    @Test
    fun `maxHlc tracks the newest event seen`() {
        val s = store()
        assertNull(s.maxHlc())
        val c = clockAt(nodeA, 1_000)
        s.append(amount(c, "txn-1", -100), 1)
        val second = amount(c, "txn-2", -200)
        s.append(second, 2)
        assertEquals(second.hlc, s.maxHlc())
    }
}
