package ie.shoonya.vitt.sheets

import ie.shoonya.vitt.model.Transaction

/**
 * The narrow slice of the Sheets API a derived-tab refresh needs.
 *
 * A port rather than the concrete [SheetsClient] so the sequencing below can be
 * tested without a network: the interesting part of this file is the order of
 * read, compare and write, and that order is exactly what a fake can exercise
 * and a live call cannot.
 */
interface DerivedTabPort {
    /** The whole tab as strings, header row included. Empty when the tab is empty. */
    suspend fun read(spreadsheetId: String, tab: String): List<List<String>>

    /** Replaces the tab from A1 and clears whatever was below. */
    suspend fun replace(
        spreadsheetId: String,
        tab: String,
        rows: List<List<Cell>>,
        lastColumn: Char,
    )
}

/**
 * Keeping the `Transactions` tab equal to the fold, without ever overwriting a
 * person's own work.
 *
 * The sequence is the design, and it is read-compare-write in that order every
 * single time. Writing first and asking later is not a shortcut here; it is the
 * loss of the only copy of whatever somebody typed.
 *
 * ## Their columns survive
 *
 * Somebody will add a `Reviewed?` column, or `Business expense`, and §2.6 says
 * plainly that they expect it kept. So the tab is rewritten *in the shape it
 * already has* — their header order, their extra columns, their values carried
 * across by row id — rather than in the app's own shape. The alternative, a
 * blanket refusal to write once an extra column exists, would mean the tab
 * silently stops updating for exactly the people who use it most.
 */
object DerivedTabs {

    const val TRANSACTIONS_TAB = "Transactions"

    sealed interface Outcome {
        /** The tab now matches the fold. */
        data class Written(val rows: Int) : Outcome

        /**
         * Somebody changed the tab, so nothing was written.
         *
         * Held rather than resolved: §2.6 wants the user offered a choice, and
         * the choice cannot be offered by code that already picked a side.
         */
        data class Held(val verdict: SheetDrift.Verdict) : Outcome
    }

    /**
     * One refresh.
     *
     * [force] is the user having answered the drift question with "keep the
     * app's version". It is the only way past a [SheetDrift.Verdict.Drifted],
     * and it is never the default.
     */
    suspend fun refresh(
        port: DerivedTabPort,
        spreadsheetId: String,
        transactions: List<Transaction>,
        accountName: (String) -> String? = { null },
        force: Boolean = false,
        tab: String = TRANSACTIONS_TAB,
    ): Outcome {
        val existing = port.read(spreadsheetId, tab)
        val desired = DerivedTransactions.table(transactions, accountName)

        val verdict = SheetDrift.compare(existing, desired)
        when (verdict) {
            is SheetDrift.Verdict.Clean -> Unit
            // A shape the app cannot read is never forced past: `force` answers
            // "whose version wins", and this is not that question.
            is SheetDrift.Verdict.Unusable -> return Outcome.Held(verdict)
            is SheetDrift.Verdict.Drifted -> if (!force) return Outcome.Held(verdict)
        }

        val shaped = inTheShapeOf(existing, desired)
        port.replace(spreadsheetId, tab, shaped, lastColumn(shaped.first().size))
        return Outcome.Written(shaped.size - 1)
    }

    /**
     * The desired table, rewritten into the column order the tab already has,
     * with anything the app does not own carried across by row id.
     *
     * A tab the app has never written has no shape of its own, so it gets the
     * app's.
     */
    internal fun inTheShapeOf(
        existing: List<List<String>>,
        desired: List<List<Cell>>,
    ): List<List<Cell>> {
        val header = existing.firstOrNull()?.map { it.trim() }?.takeIf { row ->
            row.any { it.isNotEmpty() }
        } ?: return desired

        val ours = desired.first().map { (it as Cell.Text).value }
        val idAt = header.indexOf("id")
        if (idAt < 0) return desired

        // Their cells, by row id, so a row that moved still finds its own.
        val theirs = existing.drop(1)
            .filter { it.getOrNull(idAt)?.isNotBlank() == true }
            .associateBy { it[idAt].trim() }

        val wantAt = ours.withIndex().associate { (i, name) -> name to i }
        val idInOurs = wantAt.getValue("id")

        val rows = desired.drop(1).map { want ->
            val id = (want[idInOurs] as Cell.Text).value
            val mine = theirs[id]
            header.mapIndexed { column, name ->
                wantAt[name]
                    // A column the app owns: its value, freshly computed.
                    ?.let { want[it] }
                    // A column it does not: whatever was there, untouched. A row
                    // the sheet has never seen leaves it blank rather than
                    // inventing one.
                    ?: mine?.getOrNull(column)?.takeIf { it.isNotEmpty() }?.let(Cell::Text)
                    ?: Cell.Blank
            }
        }
        return listOf(header.map(Cell::Text)) + rows
    }

    /**
     * The last column letter of a table this wide.
     *
     * Single letters only, which covers 26 columns — ten of ours plus sixteen
     * of somebody else's. Past that the write falls back to Z rather than
     * producing `A{` and a 400: a tab with twenty-seven columns is somebody
     * using the sheet as a spreadsheet, and the honest failure is the last few
     * columns going untouched rather than the whole refresh dying.
     */
    internal fun lastColumn(width: Int): Char =
        ('A' + (width - 1).coerceIn(0, 25))
}
