package ie.shoonya.vitt.sheets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SheetDriftTest {

    /** The table the app would write: header plus one row, filled positionally. */
    private fun desired(vararg rows: Map<String, String>): List<List<Cell>> =
        listOf(DerivedTransactions.COLUMNS.map { Cell.Text(it) }) +
            rows.map { row ->
                DerivedTransactions.COLUMNS.map { column ->
                    row[column]?.let { Cell.Text(it) } ?: Cell.Blank
                }
            }

    private val one = mapOf(
        "Date" to "2026-04-03",
        "Description" to "Tesco",
        "Category" to "Groceries",
        "Account" to "AIB",
        "Currency" to "EUR",
        "Amount" to "-12.50",
        "id" to "t1",
    )

    /** The same table as the sheet would hand it back, with blanks blank. */
    private fun asRead(
        columns: List<String> = DerivedTransactions.COLUMNS,
        vararg rows: Map<String, String>,
    ): List<List<Cell>> =
        listOf(columns.map { Cell.Text(it) }) +
            rows.map { row -> columns.map { row[it]?.let { v -> Cell.Text(v) } ?: Cell.Blank } }

    @Test
    fun `an empty tab is clean rather than a problem`() {
        // The ordinary first write. There is nothing to protect yet.
        assertEquals(SheetDrift.Verdict.Clean, SheetDrift.compare(emptyList(), desired(one)))
    }

    @Test
    fun `a tab holding exactly what the app wrote is clean`() {
        assertEquals(
            SheetDrift.Verdict.Clean,
            SheetDrift.compare(asRead(rows = arrayOf(one)), desired(one)),
        )
    }

    @Test
    fun `a corrected cell stops the write and says which one`() {
        // The whole point. Overwriting this silently is the worst thing the app
        // could do to a spreadsheet it asked to be trusted with.
        val edited = one + ("Category" to "Dining")
        val verdict = SheetDrift.compare(asRead(rows = arrayOf(edited)), desired(one))
        val change = (verdict as SheetDrift.Verdict.Drifted).changes.single()
        assertEquals(SheetDrift.Change.Edited("t1", "Category", "Groceries", "Dining"), change)
    }

    @Test
    fun `a row typed in by hand is reported rather than erased`() {
        val mine = mapOf("Date" to "2026-04-04", "Description" to "Cash lunch", "Amount" to "-8.00")
        val verdict = SheetDrift.compare(asRead(rows = arrayOf(one, mine)), desired(one))
        val change = (verdict as SheetDrift.Verdict.Drifted).changes.single()
        assertEquals(SheetDrift.Change.Added(null, 3), change)
    }

    @Test
    fun `a row deleted from the sheet is drift too`() {
        // Somebody deliberately removed it. Putting it straight back would be
        // the app arguing with them.
        val verdict = SheetDrift.compare(
            asRead(rows = arrayOf()),
            desired(one),
            lastSeen = SheetDrift.memoryOf(desired(one)),
        )
        assertEquals(
            listOf(SheetDrift.Change.Removed("t1")),
            (verdict as SheetDrift.Verdict.Drifted).changes,
        )
    }

    @Test
    fun `columns are found by name so an inserted column changes nothing`() {
        // Users insert columns constantly. Reading by position turns one
        // inserted column into every row compared against the wrong field.
        val shifted = listOf("Mine") + DerivedTransactions.COLUMNS
        assertEquals(
            SheetDrift.Verdict.Clean,
            SheetDrift.compare(asRead(shifted, one + ("Mine" to "x")), desired(one)),
        )
    }

    @Test
    fun `a reordered header is still read correctly`() {
        val reversed = DerivedTransactions.COLUMNS.reversed()
        assertEquals(
            SheetDrift.Verdict.Clean,
            SheetDrift.compare(asRead(reversed, one), desired(one)),
        )
    }

    @Test
    fun `somebody's own column is left out of the comparison`() {
        // Not the app's to have an opinion about, and never written either.
        val withMine = listOf("Reviewed?") + DerivedTransactions.COLUMNS
        assertEquals(
            SheetDrift.Verdict.Clean,
            SheetDrift.compare(asRead(withMine, one + ("Reviewed?" to "yes")), desired(one)),
        )
    }

    @Test
    fun `two columns with one name are unusable rather than guessed at`() {
        val doubled = DerivedTransactions.COLUMNS + "Amount"
        val verdict = SheetDrift.compare(asRead(doubled, one), desired(one))
        assertTrue(verdict is SheetDrift.Verdict.Unusable)
    }

    @Test
    fun `a missing column is a schema problem and not a question for the user`() {
        // Drift is answered by choosing a side. This cannot be, so it is a
        // different verdict with a different answer (§2.8).
        val without = DerivedTransactions.COLUMNS - "Category"
        val verdict = SheetDrift.compare(asRead(without, one), desired(one))
        assertTrue(verdict is SheetDrift.Verdict.Unusable)
        assertTrue("Category" in (verdict as SheetDrift.Verdict.Unusable).why)
    }

    @Test
    fun `a blank compares equal to the empty string the sheet returns`() {
        // Otherwise every row with no note drifts on the first read.
        assertEquals(
            SheetDrift.Verdict.Clean,
            SheetDrift.compare(asRead(rows = arrayOf(one)), desired(one)),
        )
    }

    @Test
    fun `an amount the sheet stores without its trailing zero is the same amount`() {
        // What the app writes and what a read returns differ in spelling only.
        val table = desired(one)
        val wrote = listOf(table.first()) + table.drop(1).map { row ->
            row.mapIndexed { i, cell ->
                if (DerivedTransactions.COLUMNS[i] == "Amount" && cell is Cell.Text) Cell.Number(cell.value) else cell
            }
        }
        val stored = asRead(rows = arrayOf(one + ("Amount" to "-12.5")))
        assertEquals(SheetDrift.Verdict.Clean, SheetDrift.compare(stored, wrote))
    }

    @Test
    fun `a changed amount is still an edit`() {
        val verdict = SheetDrift.compare(asRead(rows = arrayOf(one + ("Amount" to "-12.51"))), desired(one))
        val change = (verdict as SheetDrift.Verdict.Drifted).changes.single()
        assertEquals(SheetDrift.Change.Edited("t1", "Amount", "-12.50", "-12.51"), change)
    }

    @Test
    fun `decimals compare by value and never through a float`() {
        assertTrue(Cell.Number("10.00").sameValueAs(Cell.Number("10")))
        assertTrue(Cell.Number("-0.50").sameValueAs(Cell.Number("-0.5")))
        assertTrue(Cell.Number("-0").sameValueAs(Cell.Number("0")))
        // Past 2^53, where a Double would call these equal.
        assertTrue(!Cell.Number("9007199254740993").sameValueAs(Cell.Number("9007199254740992")))
        assertTrue(!Cell.Text("Tesco").sameValueAs(Cell.Text("Tesco 2")))
    }

    @Test
    fun `a row missing and never seen is new rather than deleted`() {
        // Not in the sheet because the sheet has not been given it yet.
        assertEquals(SheetDrift.Verdict.Clean, SheetDrift.compare(asRead(rows = arrayOf()), desired(one)))
    }

    @Test
    fun `a fingerprint ignores how a decimal is spelled`() {
        assertEquals(
            SheetDrift.fingerprint(listOf(Cell.Text("a"), Cell.Number("-12.50"))),
            SheetDrift.fingerprint(listOf(Cell.Text("a"), Cell.Number("-12.5"))),
        )
        assertTrue(
            SheetDrift.fingerprint(listOf(Cell.Text("a"), Cell.Blank, Cell.Text("b"))) !=
                SheetDrift.fingerprint(listOf(Cell.Text("a"), Cell.Text("b"), Cell.Blank)),
            "a value moving between columns is a change",
        )
    }

    @Test
    fun `memory survives its own encoding`() {
        val memory = mapOf("t1" to "00ff", "0190a8b2-7c3e-7000-8000-000000000001" to "abcd")
        assertEquals(memory, SheetDrift.decodeMemory(SheetDrift.encodeMemory(memory)))
        assertEquals(emptyMap(), SheetDrift.decodeMemory(null))
    }

    @Test
    fun `their own subtotal row is not reported as an addition`() {
        // Nothing in any of the app's columns, so not the app's to report,
        // and the write never touches it.
        val withMine = listOf("Notes") + DerivedTransactions.COLUMNS
        val read = asRead(withMine, one) + listOf(listOf(Cell.Text("Total so far")))
        assertEquals(SheetDrift.Verdict.Clean, SheetDrift.compare(read, desired(one)))
    }

    @Test
    fun `a short row is ordinary rather than malformed`() {
        // Sheets trims trailing empty cells, so a row is routinely narrower
        // than its header. Reading past the end has to give a blank rather
        // than throw or report every trimmed row as an edit. Shown with the
        // header reversed, which puts the always-filled id first and the
        // blank-prone columns at the end where the trimming happens.
        val idFirst = listOf("id") + (DerivedTransactions.COLUMNS - "id")
        val trimmed = asRead(idFirst, one).map { it.dropLastWhile { c -> c == Cell.Blank } }
        assertTrue(trimmed.last().size < idFirst.size, "the row under test is not actually short")
        assertEquals(SheetDrift.Verdict.Clean, SheetDrift.compare(trimmed, desired(one)))
    }

    @Test
    fun `a spacer row is skipped rather than reported as an addition`() {
        val withSpacer = asRead(rows = arrayOf(one)) + listOf(List(DerivedTransactions.COLUMNS.size) { Cell.Blank })
        assertEquals(SheetDrift.Verdict.Clean, SheetDrift.compare(withSpacer, desired(one)))
    }
}
