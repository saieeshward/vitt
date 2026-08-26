package ie.shoonya.vitt.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Events tab lives in a spreadsheet a person can edit, so its rows are
 * untrusted input rather than data this app produced.
 *
 * The failure this guards against is disproportionate: one cleared cell used to
 * throw before reaching the thousands of good rows below it, stopping sync
 * permanently for a stray keystroke.
 */
class SheetReadTest {

    private val nodeA = "a219e7a71cc18912"

    private fun goodRow(id: String, minor: Long): List<String> {
        val clock = HlcClock(nodeA, now = { 1_000L })
        return Event(clock.issue(), "transaction", id, "amount", TaggedValue.Num(minor)).toRow()
    }

    @Test
    fun `a short row does not stop the rows after it`() {
        // Sheets trims trailing empty cells, so clearing one value produces a
        // short row rather than a blank one.
        val rows = listOf(
            goodRow("txn-1", -100),
            listOf("2026-08-26T00:00:00.000Z-0000-$nodeA"),   // user cleared the rest
            goodRow("txn-2", -200),
        )
        val result = EventLog.readRows(rows)
        assertEquals(2, result.events.size, "the good rows must still sync")
        assertEquals(1, result.unreadable.size)
        assertEquals(3, result.unreadable.single().rowNumber, "reported where the user can find it")
    }

    @Test
    fun `a corrupted timestamp is isolated`() {
        val rows = listOf(
            goodRow("txn-1", -100),
            listOf("not-a-timestamp", nodeA, "transaction", "txn-x", "amount", "N:-1"),
            goodRow("txn-2", -200),
        )
        val result = EventLog.readRows(rows)
        assertEquals(2, result.events.size)
        assertEquals(1, result.unreadable.size)
    }

    @Test
    fun `an untagged value is isolated`() {
        // A user typing a bare number into the value column.
        val rows = listOf(
            listOf("2026-08-26T00:00:00.000Z-0000-$nodeA", nodeA, "transaction", "t", "amount", "1250"),
            goodRow("txn-2", -200),
        )
        val result = EventLog.readRows(rows)
        assertEquals(1, result.events.size)
        assertTrue(result.unreadable.single().reason.contains("untagged", ignoreCase = true))
    }

    @Test
    fun `blank lines are not errors`() {
        val rows = listOf(goodRow("txn-1", -100), listOf("", "", "", "", "", ""), listOf(""))
        val result = EventLog.readRows(rows)
        assertEquals(1, result.events.size)
        assertEquals(0, result.unreadable.size, "an empty row is spreadsheet noise, not corruption")
    }

    @Test
    fun `a wholly unreadable sheet reports every row rather than throwing`() {
        val rows = List(5) { listOf("garbage$it") }
        val result = EventLog.readRows(rows)
        assertEquals(0, result.events.size)
        assertEquals(5, result.unreadable.size)
    }

    @Test
    fun `the raw row is kept so the user can be shown what we saw`() {
        val bad = listOf("oops", "x")
        val result = EventLog.readRows(listOf(bad))
        assertEquals(bad, result.unreadable.single().raw)
    }
}
