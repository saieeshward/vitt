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
     * [existing] is the whole tab including its header row, read unformatted so
     * a number is its value rather than whatever the column's format displays.
     * [desired] is [DerivedTransactions.table], header included.
     *
     * [lastSeen] is this device's memory of the tab: each row's [fingerprint]
     * as it was last written or found clean. Without it the comparison can only
     * be against what the app is *about* to write, and then every entry added
     * in the app looks like a row somebody deleted from the sheet and every
     * entry edited in the app looks like a cell somebody changed. That froze
     * the tab the first time a second transaction was recorded. With it, a
     * row is somebody's edit only when it matches neither the new rendering
     * nor the last one this device knew, and a missing row is somebody's
     * deletion only when this device has seen it there before.
     *
     * Extra columns somebody added are ignored rather than reported: they are
     * not the app's to have an opinion about, and the write never touches them.
     */
    fun compare(
        existing: List<List<Cell>>,
        desired: List<List<Cell>>,
        lastSeen: Map<String, String> = emptyMap(),
    ): Verdict {
        val header = existing.firstOrNull()?.map { it.asText() }
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
        fun cellOf(row: List<Cell>, column: String): Cell =
            row.getOrNull(index.getValue(column)) ?: Cell.Blank

        val wantByIdColumn = desired.first().map { (it as? Cell.Text)?.value.orEmpty() }
        // Where each of the app's columns sits in the desired table, once.
        val wantAt = DerivedTransactions.COLUMNS.map { wantByIdColumn.indexOf(it) }
        val wantIdAt = wantByIdColumn.indexOf(ID_COLUMN)
        val want = desired.drop(1).associateBy { it[wantIdAt].asText() }

        val changes = mutableListOf<Change>()
        val seen = mutableSetOf<String>()

        existing.drop(1).forEachIndexed { offset, row ->
            // Sheets trims trailing empties, so a short row is ordinary rather
            // than malformed. A row with nothing in any of the app's columns
            // is a spacer, or somebody's own subtotal, and not the app's to
            // report: the write never touches it either.
            if (DerivedTransactions.COLUMNS.all { cellOf(row, it).asText().isBlank() }) return@forEachIndexed
            val rowNumber = offset + FIRST_DATA_ROW
            val id = row.getOrNull(idAt)?.asText().orEmpty()
            val expected = want[id]
            if (expected == null) {
                // A row the app deleted, still in the sheet until this write
                // removes it, is the app's own business and not a change.
                if (id.isEmpty() || id !in lastSeen) changes += Change.Added(id.ifEmpty { null }, rowNumber)
                return@forEachIndexed
            }
            seen += id
            val sheet = fingerprint(DerivedTransactions.COLUMNS.map { cellOf(row, it) })
            val wanted = fingerprint(wantAt.map { expected[it] })
            // Untouched by a person: it is either what the app would write now,
            // or what it was the last time this device looked, and the app has
            // simply moved on since.
            if (sheet == wanted || sheet == lastSeen[id]) return@forEachIndexed
            DerivedTransactions.COLUMNS.forEachIndexed { i, column ->
                val now = cellOf(row, column)
                val was = expected[wantAt[i]]
                if (!now.sameValueAs(was)) changes += Change.Edited(id, column, was.asText(), now.asText())
            }
        }

        // Missing and never seen is an entry the sheet has not been given yet.
        (want.keys - seen).filter { it in lastSeen }.sorted().forEach { changes += Change.Removed(it) }

        return if (changes.isEmpty()) Verdict.Clean else Verdict.Drifted(changes)
    }

    /**
     * A short, stable digest of one row's app-owned cells, compared by value so
     * `-12.50` and `-12.5` agree. FNV-1a: this only has to tell an edited row
     * from an untouched one, not resist anybody.
     */
    fun fingerprint(cells: List<Cell>): String {
        var hash = -0x340d631b7bdddcdbL // FNV offset basis
        cells.joinToString("\u001f") { it.canonicalText() }.encodeToByteArray().forEach { byte ->
            hash = hash xor (byte.toLong() and 0xFF)
            hash *= 0x100000001b3L // FNV prime
        }
        return hash.toULong().toString(16).padStart(16, '0')
    }

    /** What to remember after writing [table]: each row's fingerprint, by id. */
    fun memoryOf(table: List<List<Cell>>): Map<String, String> {
        val header = table.firstOrNull()?.map { it.asText() } ?: return emptyMap()
        val idAt = header.indexOf(ID_COLUMN).takeIf { it >= 0 } ?: return emptyMap()
        val at = DerivedTransactions.COLUMNS.map { header.indexOf(it) }
        return table.drop(1).associate { row ->
            row[idAt].asText() to fingerprint(at.map { i -> row.getOrNull(i) ?: Cell.Blank })
        }
    }

    /** Encodes a memory for a key-value store: one `id<TAB>fingerprint` per line. */
    fun encodeMemory(memory: Map<String, String>): String =
        memory.entries.joinToString("\n") { (id, fp) -> "$id\t$fp" }

    fun decodeMemory(encoded: String?): Map<String, String> =
        encoded.orEmpty().lineSequence().mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) null else line.substring(0, tab) to line.substring(tab + 1)
        }.toMap()

    private const val ID_COLUMN = "id"

    /** Row 1 is the frozen header, so data starts at 2. */
    private const val FIRST_DATA_ROW = 2
}
