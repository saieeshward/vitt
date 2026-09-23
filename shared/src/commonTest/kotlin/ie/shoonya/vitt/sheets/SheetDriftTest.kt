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

    /** The same table as the sheet would hand it back: strings, blanks empty. */
    private fun asRead(
        columns: List<String> = DerivedTransactions.COLUMNS,
        vararg rows: Map<String, String>,
    ): List<List<String>> =
        listOf(columns) + rows.map { row -> columns.map { row[it].orEmpty() } }

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
        val verdict = SheetDrift.compare(asRead(rows = arrayOf()), desired(one))
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
        // Not the app's to have an opinion about — but it is the reason the
        // write cannot be a whole-tab replace.
        val withMine = listOf("Reviewed?") + DerivedTransactions.COLUMNS
        assertEquals(
            listOf("Reviewed?"),
            SheetDrift.extraColumns(asRead(withMine, one + ("Reviewed?" to "yes"))),
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
    fun `a short row is ordinary rather than malformed`() {
        // Sheets trims trailing empty cells, so a row is routinely narrower
        // than its header. Reading past the end has to give a blank rather
        // than throw or report every trimmed row as an edit. Shown with the
        // header reversed, which puts the always-filled id first and the
        // blank-prone columns at the end where the trimming happens.
        val idFirst = listOf("id") + (DerivedTransactions.COLUMNS - "id")
        val trimmed = asRead(idFirst, one).map { it.dropLastWhile(String::isEmpty) }
        assertTrue(trimmed.last().size < idFirst.size, "the row under test is not actually short")
        assertEquals(SheetDrift.Verdict.Clean, SheetDrift.compare(trimmed, desired(one)))
    }

    @Test
    fun `a spacer row is skipped rather than reported as an addition`() {
        val withSpacer = asRead(rows = arrayOf(one)) + listOf(List(DerivedTransactions.COLUMNS.size) { "" })
        assertEquals(SheetDrift.Verdict.Clean, SheetDrift.compare(withSpacer, desired(one)))
    }
}
