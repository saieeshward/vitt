package ie.shoonya.vitt.sheets

import ie.shoonya.vitt.model.Transaction
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.time.Civil
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DerivedTabsTest {

    /** Records what was asked of it, and in what order. */
    private class FakePort(var contents: List<List<String>> = emptyList()) : DerivedTabPort {
        val calls = mutableListOf<String>()
        var written: List<List<Cell>>? = null
        var lastColumn: Char? = null

        override suspend fun read(spreadsheetId: String, tab: String): List<List<String>> {
            calls += "read"
            return contents
        }

        /** Set to fail the replace for one tab, to prove a bad year is not fatal. */
        var failFor: String? = null

        override suspend fun replace(
            spreadsheetId: String,
            tab: String,
            rows: List<List<Cell>>,
            lastColumn: Char,
        ) {
            calls += "replace:$tab"
            if (tab == failFor) throw IllegalStateException("no")
            written = rows
            this.lastColumn = lastColumn
        }

        override suspend fun ensureTab(spreadsheetId: String, tab: String) {
            calls += "ensure:$tab"
        }
    }

    private fun txn(id: String = "t1", minor: Long = -1250) = Transaction(
        id = id,
        amount = Money(minor, Currency.EUR),
        merchant = "Tesco",
        category = "groceries",
        categorySource = null,
        accountId = "a1",
        day = Civil.toDays(2026, 4, 3),
        totalPaid = null,
        splitWith = emptySet(),
        settled = Money(0, Currency.EUR),
        note = null,
        deleted = false,
    )

    private val names = { _: String -> "AIB" }

    private fun asRead(rows: List<List<Cell>>): List<List<String>> =
        rows.map { row ->
            row.map {
                when (it) {
                    is Cell.Text -> it.value
                    is Cell.Number -> it.plain
                    Cell.Blank -> ""
                }
            }
        }

    @Test
    fun `an empty tab is filled`() = runTest {
        val port = FakePort()
        val outcome = DerivedTabs.refresh(port, "s", listOf(txn()), names)
        assertEquals(DerivedTabs.Outcome.Written(1), outcome)
        assertEquals(listOf("read", "replace:Transactions"), port.calls)
    }

    @Test
    fun `the tab is read before it is written — every time`() = runTest {
        // The sequence is the design. Writing first and asking later is the
        // loss of the only copy of whatever somebody typed.
        val port = FakePort()
        DerivedTabs.refresh(port, "s", listOf(txn()), names)
        assertEquals("read", port.calls.first())
    }

    @Test
    fun `an edited cell holds the write rather than overwriting it`() = runTest {
        val current = asRead(DerivedTransactions.table(listOf(txn()), names)).toMutableList()
        current[1] = current[1].toMutableList().also {
            it[DerivedTransactions.COLUMNS.indexOf("Category")] = "Dining"
        }
        val port = FakePort(current)
        val outcome = DerivedTabs.refresh(port, "s", listOf(txn()), names)
        assertTrue(outcome is DerivedTabs.Outcome.Held)
        assertEquals(listOf("read"), port.calls, "nothing should have been written")
    }

    @Test
    fun `force is the user having answered — and only then does it write`() = runTest {
        val current = asRead(DerivedTransactions.table(listOf(txn()), names)).toMutableList()
        current[1] = current[1].toMutableList().also {
            it[DerivedTransactions.COLUMNS.indexOf("Category")] = "Dining"
        }
        val port = FakePort(current)
        val outcome = DerivedTabs.refresh(port, "s", listOf(txn()), names, force = true)
        assertEquals(DerivedTabs.Outcome.Written(1), outcome)
    }

    @Test
    fun `an unusable shape is never forced past`() = runTest {
        // force answers "whose version wins". A header the app cannot read is
        // not that question, so the answer does not apply to it.
        val port = FakePort(listOf(listOf("Date", "Date"), listOf("x", "y")))
        val outcome = DerivedTabs.refresh(port, "s", listOf(txn()), names, force = true)
        assertTrue(outcome is DerivedTabs.Outcome.Held)
        assertEquals(listOf("read"), port.calls)
    }

    @Test
    fun `somebody's own column survives the rewrite`() = runTest {
        // §2.6: users add columns and expect them kept. A refusal to write once
        // one exists would mean the tab silently stops updating for the people
        // who use it most.
        val base = DerivedTransactions.table(listOf(txn()), names)
        val header = listOf("Reviewed?") + base.first().map { (it as Cell.Text).value }
        val row = listOf("yes") + asRead(base)[1]
        val port = FakePort(listOf(header, row))

        DerivedTabs.refresh(port, "s", listOf(txn()), names)

        val out = port.written!!
        assertEquals(Cell.Text("Reviewed?"), out.first().first())
        assertEquals(Cell.Text("yes"), out[1].first())
    }

    @Test
    fun `their column order is kept rather than reimposed`() = runTest {
        val base = DerivedTransactions.table(listOf(txn()), names)
        val reversed = base.map { it.reversed() }
        val port = FakePort(asRead(reversed))

        DerivedTabs.refresh(port, "s", listOf(txn()), names)

        assertEquals(
            DerivedTransactions.COLUMNS.reversed().map { Cell.Text(it) },
            port.written!!.first(),
        )
    }

    @Test
    fun `a new row leaves their column blank rather than inventing a value`() = runTest {
        val base = DerivedTransactions.table(listOf(txn("t1")), names)
        val header = listOf("Reviewed?") + base.first().map { (it as Cell.Text).value }
        val port = FakePort(listOf(header, listOf("yes") + asRead(base)[1]))

        DerivedTabs.refresh(port, "s", listOf(txn("t1"), txn("t2")), names, force = true)

        val out = port.written!!
        assertEquals(Cell.Text("yes"), out[1].first())
        assertEquals(Cell.Blank, out[2].first())
    }

    @Test
    fun `the write is bounded by the real width of the table`() = runTest {
        val port = FakePort()
        DerivedTabs.refresh(port, "s", listOf(txn()), names)
        // Ten columns: A through J.
        assertEquals('J', port.lastColumn)
    }

    @Test
    fun `a very wide tab writes what it can rather than failing`() {
        // Past Z the A1 range would be nonsense and the refresh would 400. The
        // honest failure is the far-right columns going untouched.
        assertEquals('Z', DerivedTabs.lastColumn(40))
        assertEquals('A', DerivedTabs.lastColumn(1))
    }

    @Test
    fun `an empty ledger still writes the header`() = runTest {
        // A tab with a header and no rows reads as "nothing yet". A tab wiped
        // to nothing reads as broken.
        val port = FakePort()
        val outcome = DerivedTabs.refresh(port, "s", emptyList(), names)
        assertEquals(DerivedTabs.Outcome.Written(0), outcome)
        assertEquals(1, port.written!!.size)
    }
}

class SummaryRefreshTest {

    private class FakePort : DerivedTabPort {
        val calls = mutableListOf<String>()
        var failFor: String? = null
        override suspend fun read(spreadsheetId: String, tab: String) = emptyList<List<String>>()
        override suspend fun replace(
            spreadsheetId: String,
            tab: String,
            rows: List<List<Cell>>,
            lastColumn: Char,
        ) {
            calls += "replace:$tab"
            if (tab == failFor) throw IllegalStateException("no")
        }
        override suspend fun ensureTab(spreadsheetId: String, tab: String) {
            calls += "ensure:$tab"
        }
    }

    private fun txn(id: String, year: Int) = Transaction(
        id = id,
        amount = Money(-1250, Currency.EUR),
        merchant = "Tesco",
        category = "groceries",
        categorySource = null,
        accountId = "a1",
        day = Civil.toDays(year, 4, 3),
        totalPaid = null,
        splitWith = emptySet(),
        settled = Money(0, Currency.EUR),
        note = null,
        deleted = false,
    )

    @Test
    fun `a tab is created before it is written`() = runTest {
        // The years somebody has entries in are not knowable when the file is
        // made, so the tab cannot have been created up front.
        val port = FakePort()
        DerivedTabs.refreshSummaries(port, "s", listOf(txn("a", 2026)))
        assertEquals(listOf("ensure:Summary_2026", "replace:Summary_2026"), port.calls)
    }

    @Test
    fun `one tab per year the ledger touches`() = runTest {
        val port = FakePort()
        val written = DerivedTabs.refreshSummaries(
            port,
            "s",
            listOf(txn("a", 2024), txn("b", 2026), txn("c", 2026)),
        )
        assertEquals(listOf(2024, 2026), written)
    }

    @Test
    fun `a year that fails does not take the others with it`() = runTest {
        // A stale summary tab is worth less than the four years that wrote fine.
        val port = FakePort().also { it.failFor = "Summary_2024" }
        val written = DerivedTabs.refreshSummaries(
            port,
            "s",
            listOf(txn("a", 2024), txn("b", 2026)),
        )
        assertEquals(listOf(2026), written)
    }

    @Test
    fun `an empty ledger writes no summary tabs at all`() = runTest {
        // Summary_2026 with nothing in it is a tab somebody has to wonder about.
        val port = FakePort()
        assertTrue(DerivedTabs.refreshSummaries(port, "s", emptyList()).isEmpty())
        assertTrue(port.calls.isEmpty())
    }
}
