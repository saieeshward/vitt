package ie.shoonya.vitt.sheets

/**
 * What the `Transactions` tab looks like now, against what the app would write.
 *
 * This exists because of one line in §2.6: *the user will edit the sheet by
 * hand, so design for it as the normal case.* A derived tab is recomputed and
 * written wholesale, which is only safe while nobody has touched it — and
 * "nobody has touched it" is an assumption, not a fact. Overwriting somebody's
 * correction with a stale rendering is the single worst thing this app could do
 * to a spreadsheet it asked to be trusted with, and it would do it silently.
 *
 * So the tab is read before it is written, and a difference stops the write.
 * The app does not decide which side wins; §2.6 says it offers the choice, and
 * a choice cannot be offered by code that has already overwritten the evidence.
 *
 * ## Columns are addressed by name
 *
 * Never by index. People insert columns constantly — Tiller reports it as their
 * single largest support cost — and a positional read turns one inserted column
 * into every row being compared against the wrong field. The header row is
 * re-read every time and the names are looked up in it.
 */
object SheetDrift {

    /** Where a tab stands relative to what the app is about to write. */
    sealed interface Verdict {
        /**
         * Safe to write wholesale.
         *
         * Either the tab is empty, or every row in it is exactly what the app
         * last put there.
         */
        data object Clean : Verdict

        /**
         * Somebody changed something. Carries enough to describe it in a
         * sentence, because the user has to choose and cannot choose blind.
         */
        data class Drifted(val changes: List<Change>) : Verdict

        /**
         * The tab is not shaped like ours at all — no header, or the header is
         * missing columns this version needs.
         *
         * Distinct from [Drifted] because the answer is different: drift is a
         * question for the user, and this is a schema problem for the app
         * (§2.8, archive-then-replace).
         */
        data class Unusable(val why: String) : Verdict
    }

    sealed interface Change {
        /** A cell the app wrote and somebody else changed. */
        data class Edited(
            val id: String,
            val column: String,
            val was: String,
            val now: String,
        ) : Change

        /** A row in the sheet the app has no record of. Usually a row typed in by hand. */
        data class Added(val id: String?, val row: Int) : Change

        /** A row the app expects to write that is no longer in the sheet. */
        data class Removed(val id: String) : Change
    }

    /**
     * Compares the tab as read against the table the app would write.
     *
     * [existing] is the whole tab including its header row, as strings — which
     * is all a read can give, since the API returns formatted values and cannot
     * say whether a cell was a number or text. [desired] is
     * [DerivedTransactions.table], header included.
     *
     * Extra columns somebody added are ignored rather than reported: they are
     * not the app's to have an opinion about, and §2.6 requires they be
     * preserved. They are still a reason the write cannot be wholesale, which is
     * [extraColumns]' job.
     */
    fun compare(existing: List<List<String>>, desired: List<List<Cell>>): Verdict {
        val header = existing.firstOrNull()?.map { it.trim() }
            // An empty tab is the ordinary first case, not a problem.
            ?: return Verdict.Clean
        if (header.all { it.isEmpty() }) return Verdict.Clean

        // Two columns with one name make every lookup a coin toss, and the
        // wrong side of it silently compares the wrong field.
        val duplicates = header.filter { it.isNotEmpty() }.groupingBy { it }.eachCount()
            .filterValues { it > 1 }.keys
        if (duplicates.isNotEmpty()) {
            return Verdict.Unusable("two columns are called ${duplicates.sorted().first()}")
        }

        val index = header.withIndex().associate { (i, name) -> name to i }
        val missing = DerivedTransactions.COLUMNS.filterNot { it in index }
        if (missing.isNotEmpty()) {
            return Verdict.Unusable("the sheet has no ${missing.first()} column")
        }

        val idAt = index.getValue(ID_COLUMN)
        fun cellOf(row: List<String>, column: String): String =
            row.getOrNull(index.getValue(column)).orEmpty().trim()

        val wantByIdColumn = desired.first().map { (it as? Cell.Text)?.value.orEmpty() }
        val wantIdAt = wantByIdColumn.indexOf(ID_COLUMN)
        val want = desired.drop(1).associateBy { it[wantIdAt].text() }

        val changes = mutableListOf<Change>()
        val seen = mutableSetOf<String>()

        existing.drop(1).forEachIndexed { offset, row ->
            // Sheets trims trailing empties, so a short row is ordinary rather
            // than malformed; a row that is entirely empty is a spacer.
            if (row.all { it.isBlank() }) return@forEachIndexed
            val rowNumber = offset + FIRST_DATA_ROW
            val id = row.getOrNull(idAt)?.trim().orEmpty()
            val expected = want[id]
            if (id.isEmpty() || expected == null) {
                changes += Change.Added(id.ifEmpty { null }, rowNumber)
                return@forEachIndexed
            }
            seen += id
            DerivedTransactions.COLUMNS.forEach { column ->
                val now = cellOf(row, column)
                val was = expected[wantByIdColumn.indexOf(column)].text()
                if (now != was) changes += Change.Edited(id, column, was, now)
            }
        }

        (want.keys - seen).sorted().forEach { changes += Change.Removed(it) }

        return if (changes.isEmpty()) Verdict.Clean else Verdict.Drifted(changes)
    }

    /**
     * Columns in the sheet that the app did not put there.
     *
     * Somebody's own "Reviewed?" or "Business expense" column. §2.6 requires
     * these survive, so their presence is what turns a whole-tab write into a
     * per-column one.
     */
    fun extraColumns(existing: List<List<String>>): List<String> =
        existing.firstOrNull().orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it !in DerivedTransactions.COLUMNS }

    private const val ID_COLUMN = "id"

    /** Row 1 is the frozen header, so data starts at 2. */
    private const val FIRST_DATA_ROW = 2

    /**
     * A cell as the sheet will hand it back.
     *
     * A read cannot tell a number from text, so the comparison has to happen in
     * the one form both sides can produce — and a blank has to compare equal to
     * an empty string, because that is what Sheets returns for one.
     */
    private fun Cell.text(): String = when (this) {
        is Cell.Text -> value.trim()
        is Cell.Number -> plain
        Cell.Blank -> ""
    }
}
