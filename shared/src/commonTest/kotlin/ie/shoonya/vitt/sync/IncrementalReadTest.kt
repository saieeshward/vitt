package ie.shoonya.vitt.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Sync cost must not grow with the size of the ledger.
 *
 * Re-reading the whole sheet and materialising every key it has ever seen works
 * fine at four rows and not at all at fifty thousand — a hundred HTTP round
 * trips against a sixty-per-minute ceiling, and tens of megabytes allocated on a
 * phone, to learn nothing.
 */
class IncrementalReadTest {

    private val nodeA = "a219e7a71cc18912"

    private fun event(clock: HlcClock, id: String) =
        Event(clock.issue(), "transaction", id, "amount", TaggedValue.Num(-100))

    @Test
    fun `an existence check does not depend on history size`() {
        val store = EventStore(testDriver())
        val clock = HlcClock(nodeA, now = { 1_000L })
        val known = event(clock, "txn-1")
        store.append(known, 1L)
        val unknown = event(clock, "txn-2")

        assertTrue(store.hasEvent(known.hlc.encode()))
        assertFalse(store.hasEvent(unknown.hlc.encode()))
    }

    @Test
    fun `the predicate overload filters without copying the key set`() {
        val store = EventStore(testDriver())
        val clock = HlcClock(nodeA, now = { 1_000L })
        val held = event(clock, "txn-1")
        store.append(held, 1L)
        val fresh = event(clock, "txn-2")

        val new = EventLog.newEvents(listOf(held, fresh), store::hasEvent)
        assertEquals(listOf(fresh), new)
    }

    @Test
    fun `duplicates within one batch are still collapsed`() {
        val store = EventStore(testDriver())
        val clock = HlcClock(nodeA, now = { 1_000L })
        val e = event(clock, "txn-1")
        assertEquals(1, EventLog.newEvents(listOf(e, e, e), store::hasEvent).size)
    }

    @Test
    fun `the read cursor starts at the top and then advances`() {
        val store = EventStore(testDriver())
        assertEquals(1, store.lastReadRow(), "a fresh device reads from the beginning")

        store.rememberReadRow(500)
        assertEquals(500, store.lastReadRow())
    }

    @Test
    fun `the cursor never moves backwards`() {
        // A late-arriving shorter page must not rewind the cursor and cause the
        // whole sheet to be read again.
        val store = EventStore(testDriver())
        store.rememberReadRow(500)
        store.rememberReadRow(200)
        assertEquals(500, store.lastReadRow())
    }

    @Test
    fun `the cursor survives a restart`() {
        val driver = testDriver()
        EventStore(driver).rememberReadRow(1_234)
        assertEquals(1_234, EventStore(driver).lastReadRow())
    }
}
