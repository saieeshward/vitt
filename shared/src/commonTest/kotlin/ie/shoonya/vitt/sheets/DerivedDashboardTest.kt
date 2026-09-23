package ie.shoonya.vitt.sheets

import ie.shoonya.vitt.model.Budget
import ie.shoonya.vitt.model.LedgerRepository
import ie.shoonya.vitt.model.Transaction
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.sync.EventStore
import ie.shoonya.vitt.sync.testDriver
import ie.shoonya.vitt.time.Civil
import ie.shoonya.vitt.time.YearMonth
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DerivedDashboardTest {

    private val today = Civil.toDays(2026, 9, 21)

    private fun txn(
        id: String,
        minor: Long,
        currency: Currency = Currency.EUR,
        day: Int = today,
        category: String? = "groceries",
    ) = Transaction(
        id = id,
        amount = Money(minor, currency),
        merchant = "Tesco",
        category = category,
        categorySource = null,
        accountId = "a1",
        day = day,
        totalPaid = null,
        splitWith = emptySet(),
        settled = Money(0, currency),
        note = null,
        deleted = false,
    )

    private fun budget(currency: Currency, minor: Long) = Budget(currency, Money(minor, currency), deleted = false)

    /** The value beside a label in a currency's block. */
    private fun fact(table: List<List<Cell>>, block: Int, label: String): Cell {
        val start = DerivedDashboard.HEADER_ROWS + block * DerivedDashboard.BLOCK_ROWS
        return table.subList(start, start + DerivedDashboard.BLOCK_ROWS)
            .first { it.first() == Cell.Text(label) }[1]
    }

    @Test
    fun `each currency is its own block and nothing is summed across them`() {
        val table = DerivedDashboard.table(
            listOf(txn("a", -1250), txn("b", -50000, Currency.INR)),
            emptyMap(),
            today,
        )
        assertEquals(Cell.Text("EUR"), table[DerivedDashboard.HEADER_ROWS].first())
        assertEquals(Cell.Text("INR"), table[DerivedDashboard.HEADER_ROWS + DerivedDashboard.BLOCK_ROWS].first())
        assertEquals(Cell.Number("12.50"), fact(table, 0, "Spent this month"))
        assertEquals(Cell.Number("500.00"), fact(table, 1, "Spent this month"))
    }

    @Test
    fun `blocks are the same height so a chart keeps pointing at the right rows`() {
        val one = DerivedDashboard.table(listOf(txn("a", -1250)), emptyMap(), today)
        val two = DerivedDashboard.table(listOf(txn("a", -1250), txn("b", -500, Currency.INR, day = today + 1)), emptyMap(), today)
        assertEquals(DerivedDashboard.HEADER_ROWS + DerivedDashboard.BLOCK_ROWS, one.size)
        // A new currency is appended after the old block, which does not move.
        assertEquals(one, two.take(one.size).map { it })
        val trend = DerivedDashboard.trendHeaderRow(0)
        assertEquals(Cell.Text("Month"), one[trend].first())
        assertEquals(Cell.Text("2026-01"), one[trend + 1].first())
        assertEquals(Cell.Text("2026-12"), one[trend + 12].first())
    }

    @Test
    fun `blocks come in the order currencies first appeared`() {
        val older = txn("old", -100, Currency.INR, day = today - 200)
        assertEquals(
            listOf(Currency.INR, Currency.EUR),
            DerivedDashboard.currencies(listOf(txn("a", -1250), older)),
        )
    }

    @Test
    fun `room per day is the widget's own arithmetic`() {
        // 300.00 budget, 50.00 spent, ten days left including today: 25.00.
        val table = DerivedDashboard.table(listOf(txn("a", -5000)), mapOf(Currency.EUR to budget(Currency.EUR, 30000)), today)
        assertEquals(Cell.Number("25.00"), fact(table, 0, "Room per day"))
        assertEquals(Cell.Number("250.00"), fact(table, 0, "Left this month"))
        assertEquals(Cell.Blank, fact(table, 0, "Over this month by"))
    }

    @Test
    fun `past its budget the month says by how much — and offers no room per day`() {
        val table = DerivedDashboard.table(listOf(txn("a", -40000)), mapOf(Currency.EUR to budget(Currency.EUR, 30000)), today)
        assertEquals(Cell.Number("100.00"), fact(table, 0, "Over this month by"))
        assertEquals(Cell.Blank, fact(table, 0, "Left this month"))
        assertEquals(Cell.Blank, fact(table, 0, "Room per day"))
    }

    @Test
    fun `months still to come are blank rather than zero`() {
        val table = DerivedDashboard.table(listOf(txn("a", -1250)), emptyMap(), today)
        val trend = DerivedDashboard.trendHeaderRow(0)
        assertEquals(Cell.Number("0.00"), table[trend + 8][1], "August happened and nothing was spent")
        assertEquals(Cell.Number("12.50"), table[trend + 9][1])
        assertEquals(Cell.Blank, table[trend + 10][1], "October has not happened")
    }

    @Test
    fun `last month reaches back across new year`() {
        val january = Civil.toDays(2027, 1, 10)
        val table = DerivedDashboard.table(
            listOf(txn("dec", -2000, day = Civil.toDays(2026, 12, 20)), txn("jan", -100, day = january)),
            emptyMap(),
            january,
        )
        assertEquals(Cell.Number("20.00"), fact(table, 0, "Spent last month"))
    }

    @Test
    fun `every row is full width so nothing stale survives to its right`() {
        val table = DerivedDashboard.table(listOf(txn("a", -1250)), emptyMap(), today)
        assertTrue(table.all { it.size == DerivedDashboard.WIDTH })
    }

    @Test
    fun `the dashboard agrees with the ledger card`() {
        // If they could drift, the spreadsheet would hold a second truth.
        var t = 1_000L
        val repo = LedgerRepository(EventStore.open(testDriver(), "a219e7a71cc18912") { t++ }) { t }
        repo.record("a", Money(-1250, Currency.EUR), day = today, category = "groceries")
        repo.record("b", Money(-3000, Currency.EUR), day = today - 3, category = "dining")
        repo.record("c", Money(200000, Currency.EUR), day = today - 5, category = "salary")
        repo.setBudget(Currency.EUR, Money(50000, Currency.EUR))

        val table = DerivedDashboard.table(repo.transactions(), repo.budgets(), today)
        val ledger = repo.ledgers(YearMonth.of(today)).single { it.currency == Currency.EUR }
        assertEquals(Cell.Number(ledger.spent.toPlainString()), fact(table, 0, "Spent this month"))
        assertEquals(Cell.Number(ledger.remaining()!!.toPlainString()), fact(table, 0, "Left this month"))
    }

    private class ChartPort(var tabId: Int = 7) : DerivedTabPort {
        val calls = mutableListOf<String>()
        val charts = mutableListOf<EmbeddedChart>()
        override suspend fun read(spreadsheetId: String, tab: String) = emptyList<List<Cell>>()
        override suspend fun replace(spreadsheetId: String, tab: String, rows: List<List<Cell>>, lastColumn: Char) {
            calls += "replace:$tab"
        }
        override suspend fun archive(spreadsheetId: String, tab: String, asTab: String) = true
        override suspend fun writeBlocks(spreadsheetId: String, tab: String, blocks: List<DerivedTabs.Block>) = Unit
        override suspend fun deleteRows(spreadsheetId: String, tab: String, rows: List<Int>) = Unit
        override suspend fun sheetId(spreadsheetId: String, tab: String): Int {
            calls += "sheetId"
            return tabId
        }
        override suspend fun addCharts(spreadsheetId: String, charts: List<EmbeddedChart>) {
            calls += "charts:${charts.size}"
            this.charts += charts
        }
    }

    private suspend fun refresh(
        port: ChartPort,
        ledger: List<Transaction>,
        charted: DerivedDashboard.Charted?,
        checkCharts: Boolean = false,
    ): DerivedDashboard.Charted? {
        var kept = charted
        DerivedTabs.refreshDashboard(port, "s", ledger, emptyMap(), today, charted, checkCharts) { kept = it }
        return kept
    }

    @Test
    fun `a chart per currency is added once`() = runTest {
        val port = ChartPort()
        val ledger = listOf(txn("a", -1250), txn("b", -500, Currency.INR, day = today + 1))
        val kept = refresh(port, ledger, charted = null)
        assertEquals(listOf("replace:Dashboard", "sheetId", "charts:2"), port.calls)
        assertEquals(DerivedDashboard.Charted(7, setOf("EUR", "INR")), kept)

        port.calls.clear()
        refresh(port, ledger, kept)
        assertEquals(listOf("replace:Dashboard"), port.calls, "an ordinary refresh is the one write")
    }

    @Test
    fun `a chart somebody deleted is not put back`() = runTest {
        // The launch check looks at the tab, finds the same tab, and trusts
        // the memory: the chart being gone is their decision.
        val port = ChartPort()
        refresh(port, listOf(txn("a", -1250)), DerivedDashboard.Charted(7, setOf("EUR")), checkCharts = true)
        assertEquals(listOf("replace:Dashboard", "sheetId", "charts:0"), port.calls)
    }

    @Test
    fun `a recreated tab gets its charts again`() = runTest {
        val port = ChartPort(tabId = 99)
        val kept = refresh(port, listOf(txn("a", -1250)), DerivedDashboard.Charted(7, setOf("EUR")), checkCharts = true)
        assertEquals("charts:1", port.calls.last())
        assertEquals(DerivedDashboard.Charted(99, setOf("EUR")), kept)
    }

    @Test
    fun `a new currency gets only its own chart — beside its own block`() = runTest {
        val port = ChartPort()
        refresh(port, listOf(txn("a", -1250), txn("b", -500, Currency.INR, day = today + 1)), DerivedDashboard.Charted(7, setOf("EUR")))
        val chart = port.charts.single()
        assertEquals("INR by month", chart.spec.title)
        val domain = chart.spec.basicChart.domains.single().domain.sourceRange.sources.single()
        assertEquals(DerivedDashboard.trendHeaderRow(1), domain.startRowIndex)
        assertEquals(domain.startRowIndex + 13, domain.endRowIndex, "the header and twelve months, closed")
        assertEquals(DerivedDashboard.HEADER_ROWS + DerivedDashboard.BLOCK_ROWS, chart.position.overlayPosition.anchorCell.rowIndex)
    }

    @Test
    fun `an empty ledger writes no dashboard`() = runTest {
        val port = ChartPort()
        refresh(port, emptyList(), null, checkCharts = true)
        assertTrue(port.calls.isEmpty())
    }

    @Test
    fun `chart memory survives its own encoding`() {
        val charted = DerivedDashboard.Charted(12, setOf("EUR", "INR"))
        assertEquals(charted, DerivedDashboard.Charted.decode(charted.encode()))
        assertEquals(DerivedDashboard.Charted(12, emptySet()), DerivedDashboard.Charted.decode("12|"))
        assertEquals(null, DerivedDashboard.Charted.decode("junk"))
    }
}
