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
    /**
     * The whole tab, header row included, each cell with its type. Empty when
     * the tab is empty, or was missing and has just been created: somebody may
     * have deleted or renamed it, and it is the app's own rendering, so it
     * comes back rather than every later refresh dying on the read.
     */
    suspend fun read(spreadsheetId: String, tab: String): List<List<Cell>>

    /**
     * Replaces the tab from A1 and clears whatever was below. A tab that is not
     * there yet is created first, which is how a new year's summary appears.
     */
    suspend fun replace(
        spreadsheetId: String,
        tab: String,
        rows: List<List<Cell>>,
        lastColumn: Char,
    )

    /** Copies a tab aside under a new name. False when there was nothing to copy. */
    suspend fun archive(spreadsheetId: String, tab: String, asTab: String): Boolean

    /** Writes each block at its place, in one request. See [DerivedTabs.Block]. */
    suspend fun writeBlocks(spreadsheetId: String, tab: String, blocks: List<DerivedTabs.Block>)

    /** Deletes whole rows by 1-based number, so the sheet shifts what is below. */
    suspend fun deleteRows(spreadsheetId: String, tab: String, rows: List<Int>)

    /** The tab's numeric id, which a chart needs and A1 notation does not. */
    suspend fun sheetId(spreadsheetId: String, tab: String): Int? = null

    suspend fun addCharts(spreadsheetId: String, charts: List<EmbeddedChart>) = Unit
}

/**
 * Keeping the `Transactions` tab equal to the fold, without ever overwriting a
 * person's own work.
 *
 * The sequence is the design, and it is read-compare-write in that order every
 * single time. Writing first and asking later is not a shortcut here; it is the
 * loss of the only copy of whatever somebody typed.
 *
 * ## Their columns are never written
 *
 * Somebody will add a `Reviewed?` column, or `Business expense`, and §2.6 says
 * plainly that they expect it kept. The first version rewrote each row whole
 * and carried their cells across by row id, which kept a checkbox only once
 * reads were typed and could never keep a formula: a read returns a formula's
 * value, and writing that back replaced `=F2*0.23` with `2.88` for good.
 *
 * So the write now covers only the app's own columns, one block per run of
 * adjacent ones, and a column somebody inserted is in no block at all. Rows
 * stay where they are, found by id, which is also what keeps a formula in
 * their column pointing at its own row. A new entry goes below the last row,
 * and an entry deleted in the app has its row deleted in the sheet, natively,
 * so Sheets moves everything up and adjusts every reference as it does.
 *
 * The cost is order. A fresh tab is written oldest first, and after that an
 * entry backdated to last month lands at the bottom with today's. Sorting the
 * tab is one menu item for a person and safe here, since rows are found by id;
 * moving somebody's formulas out from under them has no undo.
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

        /**
         * A newer version of the app has shaped this file, so nothing was
         * written. See [SheetMeta] for why an older build stands back.
         */
        data class Deferred(val sheetSchema: Int) : Outcome
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
        /** This device's memory of the tab. See [SheetDrift.compare]. */
        lastSeen: Map<String, String> = emptyMap(),
        /** Called with the new memory once a write has landed, to be kept. */
        remember: (Map<String, String>) -> Unit = {},
    ): Outcome {
        val existing = port.read(spreadsheetId, tab)
        val desired = DerivedTransactions.table(transactions, accountName)

        val verdict = SheetDrift.compare(existing, desired, lastSeen)
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
                port.replace(spreadsheetId, tab, desired, lastColumn(desired.first().size))
                remember(SheetDrift.memoryOf(desired))
                return Outcome.Migrated(archivedAs, desired.size - 1)
            }
        }

        if (existing.none { row -> row.any { it.asText().isNotEmpty() } }) {
            port.replace(spreadsheetId, tab, desired, lastColumn(desired.first().size))
            remember(SheetDrift.memoryOf(desired))
            return Outcome.Written(desired.size - 1)
        }
        val plan = place(existing, desired, force)
        port.writeBlocks(spreadsheetId, tab, plan.blocks)
        // After the write, whose positions were worked out on the tab as read.
        // A delete first would move every row under it out of the place the
        // write is about to put something.
        port.deleteRows(spreadsheetId, tab, plan.deletions)
        remember(SheetDrift.memoryOf(desired))
        return Outcome.Written(desired.size - 1)
    }

    /**
     * Rewrites the `Dashboard` and adds a chart for any currency that has not
     * had one. No drift check, for the reason [refreshSummaries] gives: it is
     * arithmetic, and a number typed over it was always going to be redrawn.
     *
     * The chart step costs a request to learn the tab's id, so it runs only
     * when [checkCharts] asks (once a launch) or a currency has appeared that
     * [charted] does not cover; every other refresh is the one write.
     */
    suspend fun refreshDashboard(
        port: DerivedTabPort,
        spreadsheetId: String,
        transactions: List<Transaction>,
        budgets: Map<ie.shoonya.vitt.money.Currency, ie.shoonya.vitt.model.Budget>,
        today: Int,
        charted: DerivedDashboard.Charted?,
        checkCharts: Boolean,
        remember: (DerivedDashboard.Charted) -> Unit = {},
    ) {
        val currencies = DerivedDashboard.currencies(transactions)
        if (currencies.isEmpty()) return
        val table = DerivedDashboard.table(transactions, budgets, today)
        port.replace(spreadsheetId, DerivedDashboard.TAB, table, lastColumn(DerivedDashboard.WIDTH))

        val codes = currencies.map { it.code }
        if (!checkCharts && charted != null && charted.currencies.containsAll(codes)) return
        val sheetId = port.sheetId(spreadsheetId, DerivedDashboard.TAB) ?: return
        val known = if (charted?.sheetId == sheetId) charted.currencies else emptySet()
        val missing = currencies.withIndex().filter { it.value.code !in known }
        port.addCharts(spreadsheetId, missing.map { (i, c) -> DerivedDashboard.chart(sheetId, i, c) })
        remember(DerivedDashboard.Charted(sheetId, known + missing.map { it.value.code }))
    }

    /**
     * A rectangle of cells to write, placed by zero-based column and 1-based
     * row, as a person reads a sheet.
     */
    data class Block(val firstColumn: Int, val firstRow: Int, val rows: List<List<Cell>>)

    data class Plan(val blocks: List<Block>, val deletions: List<Int>)

    /**
     * Where each of the app's cells goes in a tab that already has rows.
     *
     * [existing] must have passed [SheetDrift.compare] as clean, or have been
     * forced: this puts the app's values over whatever is in its own columns.
     */
    internal fun place(existing: List<List<Cell>>, desired: List<List<Cell>>, force: Boolean = false): Plan {
        val header = existing.first().map { it.asText() }
        val ours = desired.first().map { it.asText() }
        // Where each of the app's columns sits in their header, in their order.
        val columns = ours.mapNotNull { name -> header.indexOf(name).takeIf { it >= 0 }?.let { name to it } }
            .sortedBy { it.second }
        val ourIndex = ours.withIndex().associate { (i, name) -> name to i }
        val idAt = header.indexOf("id")
        val idInOurs = ourIndex.getValue("id")

        val rowOf = mutableMapOf<String, Int>()
        val deletions = mutableListOf<Int>()
        existing.drop(1).forEachIndexed { offset, row ->
            val number = offset + 2
            val id = row.getOrNull(idAt)?.asText().orEmpty()
            val oursBlank = columns.all { (_, at) -> row.getOrNull(at)?.asText().isNullOrEmpty() }
            when {
                id.isNotEmpty() -> rowOf[id] = number
                // Typed in by hand. Only reachable when forced: the person
                // chose this phone's version, which does not have this row.
                force && !oursBlank -> deletions += number
            }
        }

        val want = desired.drop(1)
        val wanted = want.map { it[idInOurs].asText() }.toSet()
        rowOf.forEach { (id, number) -> if (id !in wanted) deletions += number }

        var next = existing.size + 1
        val target = mutableMapOf<Int, List<Cell>>()
        want.forEach { row -> target[rowOf[row[idInOurs].asText()] ?: next++] = row }

        val lastRow = maxOf(existing.size, next - 1)
        // A run of adjacent columns is one block from row 2 down. A row with
        // nothing new keeps what it has, written back as read, so a block can
        // be one rectangle instead of a range per cell.
        val runs = columns.fold(mutableListOf<MutableList<Pair<String, Int>>>()) { acc, column ->
            val last = acc.lastOrNull()?.last()
            if (last != null && last.second + 1 == column.second) acc.last() += column else acc += mutableListOf(column)
            acc
        }
        val blocks = runs.map { run ->
            Block(
                firstColumn = run.first().second,
                firstRow = 2,
                rows = (2..lastRow).map { number ->
                    val mine = target[number]
                    run.map { (name, at) ->
                        mine?.get(ourIndex.getValue(name))
                            ?: existing.getOrNull(number - 1)?.getOrNull(at)
                            ?: Cell.Blank
                    }
                },
            )
        }
        return Plan(blocks, deletions.distinct().sorted())
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
     * The last column letter of a table this wide.
     *
     * Single letters only, which covers 26 columns — ten of ours plus sixteen
     * of somebody else's. Past that the write falls back to Z rather than
     * producing `A{` and a 400: a tab with twenty-seven columns is somebody
     * using the sheet as a spreadsheet, and the honest failure is the last few
     * columns going untouched rather than the whole refresh dying.
     */
    internal fun lastColumn(width: Int): Char =
        columnName((width - 1).coerceIn(0, 25)).single()

    /** `A` for 0, `Z` for 25, `AA` for 26: the A1 name of a zero-based column. */
    internal fun columnName(index: Int): String {
        var n = index + 1
        val out = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % 26
            out.insert(0, 'A' + r)
            n = (n - 1) / 26
        }
        return out.toString()
    }
}
