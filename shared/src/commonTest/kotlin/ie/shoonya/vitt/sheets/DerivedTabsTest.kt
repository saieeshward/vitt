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

    /**
     * Records what was asked of it, and in what order, and keeps a grid the way
     * a sheet would, so a test can look at the tab a refresh leaves behind.
     */
    private class FakePort(var contents: List<List<Cell>> = emptyList()) : DerivedTabPort {
        val calls = mutableListOf<String>()
        var lastColumn: Char? = null
        var blocks: List<DerivedTabs.Block> = emptyList()

        /** The tab as it stands after the refresh, or null if nothing was written. */
        val written: List<List<Cell>>? get() = if (calls.any { it.startsWith("replace") || it.startsWith("write") }) contents else null

        override suspend fun read(spreadsheetId: String, tab: String): List<List<Cell>> {
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
            contents = rows
            this.lastColumn = lastColumn
        }

        override suspend fun archive(spreadsheetId: String, tab: String, asTab: String): Boolean {
            calls += "archive:$asTab"
            return true
        }

        override suspend fun writeBlocks(spreadsheetId: String, tab: String, blocks: List<DerivedTabs.Block>) {
            calls += "write:$tab"
            this.blocks = blocks
            val grid = contents.map { it.toMutableList() }.toMutableList()
            blocks.forEach { block ->
                block.rows.forEachIndexed { r, row ->
                    val at = block.firstRow - 1 + r
                    while (grid.size <= at) grid += mutableListOf<Cell>()
                    row.forEachIndexed { c, cell ->
                        val column = block.firstColumn + c
                        while (grid[at].size <= column) grid[at] += Cell.Blank
                        grid[at][column] = cell
                    }
                }
            }
            contents = grid
        }

        override suspend fun deleteRows(spreadsheetId: String, tab: String, rows: List<Int>) {
            if (rows.isEmpty()) return
            calls += "delete:${rows.joinToString(",")}"
            contents = contents.filterIndexed { i, _ -> (i + 1) !in rows }
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

    /**
     * The table as an unformatted read hands it back. A stored number has no
     * trailing zeros, so the -12.50 the app wrote comes back as -12.5.
     */
    private fun asRead(rows: List<List<Cell>>): List<List<Cell>> =
        rows.map { row ->
            row.map {
                if (it is Cell.Number && '.' in it.plain) {
                    Cell.Number(it.plain.trimEnd('0').trimEnd('.'))
                } else {
                    it
                }
            }
        }

    private fun texts(vararg values: String): List<Cell> = values.map { Cell.Text(it) }

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
            it[DerivedTransactions.COLUMNS.indexOf("Category")] = Cell.Text("Dining")
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
            it[DerivedTransactions.COLUMNS.indexOf("Category")] = Cell.Text("Dining")
        }
        val port = FakePort(current)
        val outcome = DerivedTabs.refresh(port, "s", listOf(txn()), names, force = true)
        assertEquals(DerivedTabs.Outcome.Written(1), outcome)
    }

    @Test
    fun `an unreadable shape is archived and rewritten rather than frozen`() = runTest {
        // §2.8's archive-then-replace, run without asking. The plan offers
        // three choices, which is right for a tab somebody built; this one the
        // app renders, so a copy loses nothing. Set against that, asking means
        // the tab stops updating until a line in Settings is noticed and
        // understood — and that will happen to everybody at once the day a
        // column is added in an update.
        val port = FakePort(listOf(texts("Date", "Date"), texts("x", "y")))
        val outcome = DerivedTabs.refresh(
            port, "s", listOf(txn()), names,
            today = Civil.toDays(2026, 9, 23),
        )
        assertEquals(
            DerivedTabs.Outcome.Migrated("Transactions_2026-09-23", 1),
            outcome,
        )
        assertEquals(
            listOf("read", "archive:Transactions_2026-09-23", "replace:Transactions"),
            port.calls,
        )
    }

    @Test
    fun `the archive is taken before anything is written over`() = runTest {
        // The order is the guarantee. Writing first and copying after copies
        // the new thing.
        val port = FakePort(listOf(texts("Date", "Date"), texts("x", "y")))
        DerivedTabs.refresh(port, "s", listOf(txn()), names, today = 1)
        assertTrue(port.calls.indexOf("archive:Transactions_1970-01-02") < port.calls.indexOf("replace:Transactions"))
    }

    @Test
    fun `an archive is named for the day it was taken`() {
        // A person opening the file months later needs to know when the old
        // one was set aside; a _v2 tells them nothing they can match against
        // their own memory.
        assertEquals(
            "Transactions_2026-09-23",
            DerivedTabs.archiveName("Transactions", Civil.toDays(2026, 9, 23)),
        )
    }

    @Test
    fun `a missing column is a shape problem rather than a question`() = runTest {
        // The near-term case: an update adds a column, and every existing
        // spreadsheet has the old header.
        val header = DerivedTransactions.COLUMNS - "Category"
        val port = FakePort(listOf(header.map { Cell.Text(it) }, header.map { Cell.Text("x") }))
        val outcome = DerivedTabs.refresh(port, "s", listOf(txn()), names, today = 1)
        assertTrue(outcome is DerivedTabs.Outcome.Migrated)
    }

    @Test
    fun `somebody's own column survives the rewrite`() = runTest {
        // §2.6: users add columns and expect them kept. A refusal to write once
        // one exists would mean the tab silently stops updating for the people
        // who use it most.
        val base = DerivedTransactions.table(listOf(txn()), names)
        val header = texts("Reviewed?") + base.first()
        val row = texts("yes") + asRead(base)[1]
        val port = FakePort(listOf(header, row))

        DerivedTabs.refresh(port, "s", listOf(txn()), names)

        val out = port.written!!
        assertEquals(Cell.Text("Reviewed?"), out.first().first())
        assertEquals(Cell.Text("yes"), out[1].first())
    }

    @Test
    fun `a second refresh over the app's own write is clean — not an edit`() = runTest {
        // The app writes -12.50 and the sheet stores -12.5. Compared as
        // spellings that was an edit on every row with a round number of
        // cents, so the tab held itself on the second refresh and never
        // updated again.
        val first = FakePort()
        DerivedTabs.refresh(first, "s", listOf(txn(minor = -1250), txn("t2", minor = -1000)), names)
        val port = FakePort(asRead(first.written!!))

        val outcome = DerivedTabs.refresh(port, "s", listOf(txn(minor = -1250), txn("t2", minor = -1000)), names)

        assertEquals(DerivedTabs.Outcome.Written(2), outcome)
    }

    @Test
    fun `a checkbox in their own column goes back a checkbox`() = runTest {
        // Written back as the text TRUE it fails the checkbox's own validation
        // and stops being one. Reviewed? is the column §2.6 names.
        val base = DerivedTransactions.table(listOf(txn()), names)
        val port = FakePort(
            listOf(texts("Reviewed?") + base.first(), listOf(Cell.Bool(true)) + asRead(base)[1]),
        )

        DerivedTabs.refresh(port, "s", listOf(txn()), names)

        assertEquals(Cell.Bool(true), port.written!![1].first())
    }

    @Test
    fun `a date in their own column goes back as the number it is`() = runTest {
        // An unformatted read gives a date as its serial number. Sent back as a
        // number, the cell's date format still has something to format.
        val base = DerivedTransactions.table(listOf(txn()), names)
        val port = FakePort(
            listOf(texts("Paid on") + base.first(), listOf(Cell.Number("46287")) + asRead(base)[1]),
        )

        DerivedTabs.refresh(port, "s", listOf(txn()), names)

        assertEquals(Cell.Number("46287"), port.written!![1].first())
    }

    /** The app's table with somebody's own column inserted before [before]. */
    private fun withTheirColumn(
        name: String,
        before: String,
        base: List<List<Cell>>,
        value: (Int) -> Cell,
    ): List<List<Cell>> {
        val at = DerivedTransactions.COLUMNS.indexOf(before)
        return base.mapIndexed { i, row ->
            row.toMutableList().also { it.add(at, if (i == 0) Cell.Text(name) else value(i)) }
        }
    }

    @Test
    fun `a column they inserted is in no block — so a formula in it is never written`() = runTest {
        // A read returns a formula's value. Rewriting the row whole replaced
        // =F2*0.23 with its value for good; the only safe write is none.
        val base = asRead(DerivedTransactions.table(listOf(txn("t1"), txn("t2")), names))
        val tab = withTheirColumn("VAT", before = "Account", base) { Cell.Number("2.88") }
        val vatAt = tab.first().indexOf(Cell.Text("VAT"))
        val port = FakePort(tab)

        DerivedTabs.refresh(
            port, "s", listOf(txn("t1"), txn("t2", minor = -2000)), names,
            lastSeen = SheetDrift.memoryOf(tab),
        )

        port.blocks.forEach { block ->
            assertTrue(
                vatAt !in block.firstColumn until block.firstColumn + block.rows.first().size,
                "a block covers their column: $block",
            )
        }
        assertEquals(2, port.blocks.size, "one block either side of their column")
    }

    @Test
    fun `rows stay where they are when somebody has sorted the tab`() = runTest {
        // Found by id rather than rewritten in the app's order, so their own
        // cells and anything pointing at a row keep meaning that row.
        val base = asRead(DerivedTransactions.table(listOf(txn("t1"), txn("t2")), names))
        val sorted = listOf(base[0], base[2], base[1])
        val port = FakePort(sorted)

        DerivedTabs.refresh(
            port, "s", listOf(txn("t1", minor = -999), txn("t2")), names,
            lastSeen = SheetDrift.memoryOf(sorted),
        )

        val idAt = DerivedTransactions.COLUMNS.indexOf("id")
        assertEquals(listOf("t2", "t1"), port.written!!.drop(1).map { it[idAt].asText() })
        val amountAt = DerivedTransactions.COLUMNS.indexOf("Amount")
        assertEquals(Cell.Number("-9.99"), port.written!![2][amountAt])
    }

    @Test
    fun `a new entry goes below the last row`() = runTest {
        val base = asRead(DerivedTransactions.table(listOf(txn("t1")), names))
        val port = FakePort(base)

        DerivedTabs.refresh(port, "s", listOf(txn("t1"), txn("t2")), names, force = true)

        val idAt = DerivedTransactions.COLUMNS.indexOf("id")
        assertEquals(listOf("t1", "t2"), port.written!!.drop(1).map { it[idAt].asText() })
    }

    @Test
    fun `an entry deleted in the app has its row deleted — after the write`() = runTest {
        // Natively, so Sheets moves what is below up and adjusts references.
        // After, because the write's positions were worked out on the tab as
        // read.
        val base = asRead(DerivedTransactions.table(listOf(txn("t1"), txn("t2"), txn("t3")), names))
        val port = FakePort(base)

        DerivedTabs.refresh(port, "s", listOf(txn("t1"), txn("t3")), names, force = true)

        assertEquals(listOf("read", "write:Transactions", "delete:3"), port.calls)
        val idAt = DerivedTransactions.COLUMNS.indexOf("id")
        assertEquals(listOf("t1", "t3"), port.written!!.drop(1).map { it[idAt].asText() })
    }

    @Test
    fun `replacing with this phone's version deletes a hand-typed row and keeps their subtotal`() = runTest {
        val base = asRead(DerivedTransactions.table(listOf(txn("t1")), names))
        val tab = withTheirColumn("Notes", before = "Date", base) { Cell.Text("mine") }
        val width = tab.first().size
        val handTyped = List(width) { i -> if (i == 2) Cell.Text("Cash lunch") else Cell.Blank }
        val subtotal = List(width) { i -> if (i == 0) Cell.Text("Total so far") else Cell.Blank }
        val port = FakePort(tab + listOf(handTyped, subtotal))

        DerivedTabs.refresh(port, "s", listOf(txn("t1")), names, force = true)

        assertEquals("delete:3", port.calls.last())
        assertTrue(port.written!!.any { it.firstOrNull() == Cell.Text("Total so far") })
    }

    @Test
    fun `an entry recorded after the last write is added — not held as a deleted row`() = runTest {
        // The bug memory fixes. Compared only against what the app was about
        // to write, t2 was missing from the sheet and read as somebody's
        // deletion, so the tab held the first time a second entry was made.
        val first = FakePort()
        var memory = emptyMap<String, String>()
        DerivedTabs.refresh(first, "s", listOf(txn("t1")), names, remember = { memory = it })
        val port = FakePort(asRead(first.written!!))

        val outcome = DerivedTabs.refresh(
            port, "s", listOf(txn("t1"), txn("t2")), names, lastSeen = memory, remember = { memory = it },
        )

        assertEquals(DerivedTabs.Outcome.Written(2), outcome)
        assertEquals(setOf("t1", "t2"), memory.keys)
    }

    @Test
    fun `an entry changed in the app is written — not held as somebody's edit`() = runTest {
        val first = FakePort()
        var memory = emptyMap<String, String>()
        DerivedTabs.refresh(first, "s", listOf(txn("t1")), names, remember = { memory = it })
        val port = FakePort(asRead(first.written!!))

        val outcome = DerivedTabs.refresh(port, "s", listOf(txn("t1", minor = -4000)), names, lastSeen = memory)

        assertEquals(DerivedTabs.Outcome.Written(1), outcome)
        val amountAt = DerivedTransactions.COLUMNS.indexOf("Amount")
        assertEquals(Cell.Number("-40.00"), port.written!![1][amountAt])
    }

    @Test
    fun `an entry deleted in the app is removed — not held as a row nobody expected`() = runTest {
        val first = FakePort()
        var memory = emptyMap<String, String>()
        DerivedTabs.refresh(first, "s", listOf(txn("t1"), txn("t2")), names, remember = { memory = it })
        val port = FakePort(asRead(first.written!!))

        val outcome = DerivedTabs.refresh(port, "s", listOf(txn("t1")), names, lastSeen = memory)

        assertEquals(DerivedTabs.Outcome.Written(1), outcome)
        assertEquals("delete:3", port.calls.last())
    }

    @Test
    fun `somebody's edit is still held when the app has moved on too`() = runTest {
        // Memory must not become a way past a person's change: a cell that
        // matches neither the new rendering nor the last one is theirs.
        val first = FakePort()
        var memory = emptyMap<String, String>()
        DerivedTabs.refresh(first, "s", listOf(txn("t1")), names, remember = { memory = it })
        val edited = asRead(first.written!!).toMutableList()
        edited[1] = edited[1].toMutableList().also {
            it[DerivedTransactions.COLUMNS.indexOf("Category")] = Cell.Text("Dining")
        }
        val port = FakePort(edited)

        val outcome = DerivedTabs.refresh(port, "s", listOf(txn("t1", minor = -4000)), names, lastSeen = memory)

        assertTrue(outcome is DerivedTabs.Outcome.Held, "$outcome")
        assertEquals(listOf("read"), port.calls)
    }

    @Test
    fun `column names run past Z the way a sheet names them`() {
        assertEquals("A", DerivedTabs.columnName(0))
        assertEquals("Z", DerivedTabs.columnName(25))
        assertEquals("AA", DerivedTabs.columnName(26))
        assertEquals("AZ", DerivedTabs.columnName(51))
        assertEquals("BA", DerivedTabs.columnName(52))
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
        val header = texts("Reviewed?") + base.first()
        val port = FakePort(listOf(header, texts("yes") + asRead(base)[1]))

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
        override suspend fun read(spreadsheetId: String, tab: String) = emptyList<List<Cell>>()
        override suspend fun replace(
            spreadsheetId: String,
            tab: String,
            rows: List<List<Cell>>,
            lastColumn: Char,
        ) {
            calls += "replace:$tab"
            if (tab == failFor) throw IllegalStateException("no")
        }

        override suspend fun archive(spreadsheetId: String, tab: String, asTab: String) = true
        override suspend fun writeBlocks(spreadsheetId: String, tab: String, blocks: List<DerivedTabs.Block>) = Unit
        override suspend fun deleteRows(spreadsheetId: String, tab: String, rows: List<Int>) = Unit
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
    fun `a summary is one write per year and no separate create`() = runTest {
        // The years somebody has entries in are not knowable when the file is
        // made. The port creates a missing tab on the write that finds it
        // missing, so an ordinary sync spends no request on creating anything.
        val port = FakePort()
        DerivedTabs.refreshSummaries(port, "s", listOf(txn("a", 2026)))
        assertEquals(listOf("replace:Summary_2026"), port.calls)
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
