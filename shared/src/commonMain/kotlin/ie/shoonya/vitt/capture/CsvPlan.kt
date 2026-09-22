package ie.shoonya.vitt.capture

import ie.shoonya.vitt.money.Money

/**
 * One row, resolved as far as it can be before anything is written.
 *
 * Separate from [CsvRow] because a parsed row is not yet an entry: it still has
 * a date that may not read, an amount that may be zero, and no account. This is
 * the row after those questions are answered, and it is what the confirm screen
 * shows.
 */
data class PlannedRow(
    val lineNumber: Int,
    val day: Int?,
    val merchant: String?,
    val amount: Money?,
    /** Why this row cannot be imported, or empty when it can. */
    val problems: List<String>,
) {
    val isImportable: Boolean get() = day != null && amount != null && problems.isEmpty()
}

/**
 * What an import would do, worked out in full before a single row is written.
 *
 * Nothing about a CSV import is undoable. The ledger is an append-only event
 * log, so five hundred rows imported by mistake are five hundred rows that have
 * to be deleted one at a time, and every one of them has already been pushed to
 * the user's own spreadsheet. So the whole file is resolved first, shown, and
 * only written once somebody has looked at it.
 *
 * The count that matters on that screen is [skipped], not [importable].
 * A person will happily accept "412 entries" without reading it; what they need
 * to see is that nine rows will be silently dropped and why.
 */
data class CsvPlan(
    val rows: List<PlannedRow>,
    val order: CsvDate.Order,
) {
    val importable: List<PlannedRow> get() = rows.filter { it.isImportable }
    val skipped: List<PlannedRow> get() = rows.filterNot { it.isImportable }

    /** True when the file cannot be read until the user says which way dates run. */
    val needsDateOrder: Boolean
        get() = order == CsvDate.Order.AMBIGUOUS && rows.any { it.day == null }

    /** The span the file covers, for the confirm screen. Null when nothing is importable. */
    val dayRange: IntRange?
        get() = importable.mapNotNull { it.day }.let {
            if (it.isEmpty()) null else it.min()..it.max()
        }

    companion object {
        /**
         * Resolves a parsed file against a chosen date order.
         *
         * [order] is passed rather than derived so the caller can re-plan after
         * the user resolves an ambiguous file, without re-reading it.
         */
        fun of(result: CsvImportResult, order: CsvDate.Order): CsvPlan = CsvPlan(
            order = order,
            rows = result.rows.map { row ->
                val day = CsvDate.parse(row.date, order)
                val problems = buildList {
                    addAll(row.problems)
                    if (row.amount == null) add("no amount")
                    // A zero row records nothing and `record` refuses it, so it
                    // is caught here rather than thrown halfway through a file.
                    else if (row.amount.minor == 0L) add("zero amount")
                    if (day == null) {
                        add(
                            if (order == CsvDate.Order.AMBIGUOUS) "date could be either way round"
                            else "date not understood",
                        )
                    }
                }
                PlannedRow(
                    lineNumber = row.lineNumber,
                    day = day,
                    merchant = row.description?.let(MerchantName::clean) ?: row.description,
                    amount = row.amount,
                    problems = problems,
                )
            },
        )
    }
}
