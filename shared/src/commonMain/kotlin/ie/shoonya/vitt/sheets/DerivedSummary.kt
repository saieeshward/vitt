package ie.shoonya.vitt.sheets

import ie.shoonya.vitt.model.Transaction
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.time.YearMonth

/** One month, one currency, one category. The grain the whole tab is written at. */
data class SummaryRow(
    val month: YearMonth,
    val currency: Currency,
    val category: String?,
    val spent: Money,
    val received: Money,
    val entries: Int,
)

/**
 * `Summary_YYYY`: the pre-aggregated tab a dashboard is allowed to read.
 *
 * §2.7 is unambiguous that a chart must never sit on the raw transaction range.
 * Value-pasted aggregates are called the single highest-leverage change
 * available, because they take the history out of the dependency graph
 * entirely — a formula over fifty thousand rows recalculates on every edit
 * anywhere in the file, and an aggregate does not recalculate at all.
 *
 * So these are values, written by the app, and there is not a formula anywhere
 * in the tab. That is also what keeps §0.5 intact: the app never trusts a number
 * the sheet computed, and the surest way not to is for the sheet to compute
 * nothing.
 *
 * ## It has to agree with the app
 *
 * The aggregate is the same arithmetic as `LedgerRepository.ledgers`, down to
 * which rows count. If it were not, the spreadsheet would hold a second and
 * quietly different truth, and the person reading it would have no way to tell
 * which one was wrong. Specifically: a category that does not move money is
 * excluded, a split counts the user's own share rather than what they fronted,
 * and spending and income are separate magnitudes rather than a net.
 *
 * ## One tab per year
 *
 * Not one tab for everything. A year is the natural archive unit here — §2.7's
 * escape at fifty thousand rows is `Transactions_YYYY`, and a summary already
 * partitioned the same way needs no migration when that day comes. It also
 * keeps each tab small enough that a dashboard reads all of it.
 */
object DerivedSummary {

    val COLUMNS = listOf("Month", "Currency", "Category", "Spent", "Received", "Entries")

    /** `Summary_2026`. */
    fun tabFor(year: Int): String = "Summary_$year"

    /** Every year the ledger has something in, so the caller knows which tabs to write. */
    fun years(transactions: List<Transaction>): List<Int> = transactions
        .filterNot { it.deleted }
        .map { YearMonth.of(it.day).year }
        .distinct()
        .sorted()

    /**
     * The rows for one year, ordered so the tab reads like a report rather than
     * like a dump: month, then currency, then category.
     */
    fun rows(transactions: List<Transaction>, year: Int): List<SummaryRow> = transactions
        .filterNot { it.deleted }
        // A transfer between your own accounts is not spending and not income.
        // `movesMoney` is the same gate the ledger card uses, and the reason
        // the two figures agree.
        .filter { it.movesMoney }
        .filter { YearMonth.of(it.day).year == year }
        .groupBy { Triple(YearMonth.of(it.day), it.amount.currency, it.category) }
        .map { (key, rows) ->
            val (month, currency, category) = key
            SummaryRow(
                month = month,
                currency = currency,
                category = category,
                // Magnitudes, and separately. A net would make a month where
                // salary and rent both landed look like a quiet one, and a
                // dashboard built on it would be confidently wrong.
                spent = rows.filter { it.isSpend }
                    .fold(Money(0, currency)) { acc, t -> acc + t.amount.abs() },
                received = rows.filter { it.isIncome }
                    .fold(Money(0, currency)) { acc, t -> acc + t.amount },
                entries = rows.size,
            )
        }
        .sortedWith(
            compareBy(
                { it.month.month },
                { it.currency.code },
                // Uncategorised last: it is the pile to work through, not a
                // category, and sorting it into the As would hide that.
                { it.category ?: "￿" },
            )
        )

    /** The tab for one year, header included. */
    fun table(transactions: List<Transaction>, year: Int): List<List<Cell>> =
        listOf(COLUMNS.map { Cell.Text(it) }) +
            rows(transactions, year).map { row ->
                listOf(
                    Cell.Text(row.month.toString()),
                    Cell.Text(row.currency.code),
                    // The label a person recognises, not the stored code.
                    Cell.Text(
                        row.category
                            ?.let { ie.shoonya.vitt.capture.Category.ofCode(it)?.label ?: it }
                            ?: UNCATEGORISED
                    ),
                    Cell.Number(row.spent.toPlainString()),
                    Cell.Number(row.received.toPlainString()),
                    Cell.Number(row.entries.toString()),
                )
            }

    /**
     * Named rather than blank.
     *
     * A blank cell in a category column reads as a missing value and sorts
     * unpredictably in a pivot; this is a real group with a real total, and it
     * is one a person may well want to click into.
     */
    const val UNCATEGORISED = "Uncategorised"
}
