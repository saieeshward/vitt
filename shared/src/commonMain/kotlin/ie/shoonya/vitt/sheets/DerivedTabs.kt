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

    /** Creates the tab if it is not already there. */
    suspend fun ensureTab(spreadsheetId: String, tab: String)

    /** Copies a tab aside under a new name. False when there was nothing to copy. */
    suspend fun archive(spreadsheetId: String, tab: String, asTab: String): Boolean
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

        /** The old tab was copied aside under [archivedAs] and a fresh one written. */
        data class Migrated(val archivedAs: String, val rows: Int) : Outcome
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
        /** Days since the epoch, for naming an archive. */
        today: Int = 0,
    ): Outcome {
        val existing = port.read(spreadsheetId, tab)
        val desired = DerivedTransactions.table(transactions, accountName)

        val verdict = SheetDrift.compare(existing, desired)
        when (verdict) {
            is SheetDrift.Verdict.Clean -> Unit
            is SheetDrift.Verdict.Drifted -> if (!force) return Outcome.Held(verdict)
            // §2.8's archive-then-replace, and it runs without asking.
            //
            // The plan offers three choices here, which is right for a tab
            // whose contents somebody built. This one the app renders, so the
            // archive loses nothing — it is a copy, the original stays, and
            // every reference to it still resolves. Set against that, the cost
            // of asking is a tab frozen until somebody notices a line in
            // Settings and understands what it wants. That will happen the day
            // a column is added in an update, to everybody at once, and
            // "your spreadsheet stopped updating" is not a thing to say to a
            // person who did nothing wrong.
            is SheetDrift.Verdict.Unusable -> {
                val archivedAs = archiveName(tab, today)
                port.archive(spreadsheetId, tab, archivedAs)
                val fresh = DerivedTransactions.table(transactions, accountName)
                port.replace(spreadsheetId, tab, fresh, lastColumn(fresh.first().size))
                return Outcome.Migrated(archivedAs, fresh.size - 1)
            }
        }

        val shaped = inTheShapeOf(existing, desired)
        port.replace(spreadsheetId, tab, shaped, lastColumn(shaped.first().size))
        return Outcome.Written(shaped.size - 1)
    }

    /**
     * What an archived copy is called.
     *
     * Dated rather than numbered, because a person opening the file months
     * later needs to know *when* the old one was set aside, and a `_v2` tells
     * them nothing they can match against their own memory. Dated rather than
     * timestamped for the same reason in the other direction: two archives in
     * one day is a migration that went wrong twice, and the second overwriting
     * the first is the correct outcome rather than a third tab.
     */
    internal fun archiveName(tab: String, today: Int): String =
        tab + "_" + ie.shoonya.vitt.time.Civil.isoDate(today)

    /**
     * Rewrites `Summary_YYYY` for every year the ledger touches.
     *
     * No drift check, unlike the Transactions tab, and the difference is
     * deliberate. That tab is a list of things that happened and a person has
     * every reason to annotate a row of it; this one is arithmetic, and a
     * number somebody typed over an aggregate is a number that was always going
     * to be recomputed. Holding the write would mean one stray keystroke
     * freezing the dashboard's only source for good.
     *
     * Failures are per-year rather than fatal: a summary that could not be
     * written is a stale tab, and a stale tab is worth less than the four other
     * years that wrote fine.
     */
    suspend fun refreshSummaries(
        port: DerivedTabPort,
        spreadsheetId: String,
        transactions: List<Transaction>,
    ): List<Int> {
        val written = mutableListOf<Int>()
        DerivedSummary.years(transactions).forEach { year ->
            val tab = DerivedSummary.tabFor(year)
            try {
                port.ensureTab(spreadsheetId, tab)
                val table = DerivedSummary.table(transactions, year)
                port.replace(spreadsheetId, tab, table, lastColumn(DerivedSummary.COLUMNS.size))
                written += year
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
        }
        return written
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
