package ie.shoonya.vitt.sheets

import ie.shoonya.vitt.capture.Category
import ie.shoonya.vitt.model.Budget
import ie.shoonya.vitt.model.Transaction
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.money.sumMoney
import ie.shoonya.vitt.time.Civil
import ie.shoonya.vitt.time.YearMonth
import ie.shoonya.vitt.widget.WidgetSnapshot

/**
 * The `Dashboard` tab: the page somebody opens the spreadsheet to look at.
 *
 * §2.2 puts it over a pre-aggregated summary and never over the raw range, and
 * this goes one step further than a formula over `Summary_YYYY` would: it is
 * values, written by the app, like the summary itself. No formula means nothing
 * to recalculate, nothing the app could be tempted to read back and trust
 * (§0.5), and no way for this page to disagree with the app, because every
 * figure comes out of the same arithmetic. Months and categories are
 * [DerivedSummary.rows], which is tested to agree with the ledger card, and room
 * per day is the widget's own [WidgetSnapshot.roomPerDay].
 *
 * ## One block per currency, and the blocks do not move
 *
 * §0.6 rules out a total across currencies, so each currency is its own block
 * and nothing on the page is converted. Every block is the same height, and
 * blocks are placed in the order each currency first appeared in the ledger,
 * which only ever appends. That is what lets a chart, the app's or somebody's
 * own, point at a block's trend rows and keep pointing at the right ones as
 * the months fill in and new currencies arrive. The one thing that moves a
 * block is a backdated import in a currency older than the others, which is
 * rare and costs a redrawn chart rather than a wrong one.
 *
 * ## Tone
 *
 * This is the page most like a verdict, so it is worded as facts about
 * pockets. There is no "over budget" in red: when a month is past its budget
 * the row says by how much, next to a room-per-day that is simply blank.
 */
object DerivedDashboard {

    const val TAB = "Dashboard"

    /** Rows above the first block: a title, a line of explanation, a gap. */
    const val HEADER_ROWS = 3

    /** Every block's height, blank-filled, so the next one always starts in the same place. */
    const val BLOCK_ROWS = 30

    /** How many categories the "top this month" list shows. */
    const val TOP = 5

    /** Where in a block the twelve-month trend starts, counted from the block's first row. */
    const val TREND_OFFSET = 17

    const val WIDTH = 3

    /** The currencies, in the order their blocks are laid out. */
    fun currencies(transactions: List<Transaction>): List<Currency> = transactions
        .filterNot { it.deleted }
        .filter { it.movesMoney }
        .groupBy { it.amount.currency }
        .mapValues { (_, rows) -> rows.minOf { it.day } }
        .entries
        .sortedWith(compareBy({ it.value }, { it.key.code }))
        .map { it.key }

    /**
     * The 0-based row of a currency's trend header, for a chart's source range.
     * The trend's twelve months follow it.
     */
    fun trendHeaderRow(blockIndex: Int): Int = HEADER_ROWS + blockIndex * BLOCK_ROWS + TREND_OFFSET

    /** The whole tab. */
    fun table(
        transactions: List<Transaction>,
        budgets: Map<Currency, Budget>,
        today: Int,
    ): List<List<Cell>> {
        val month = YearMonth.of(today)
        val thisYear = DerivedSummary.rows(transactions, month.year)
        val lastMonth = month.previous()
        val lastMonthRows = if (lastMonth.year == month.year) thisYear else DerivedSummary.rows(transactions, lastMonth.year)

        val rows = mutableListOf<List<Cell>>()
        rows += listOf(Cell.Text("VITT"), Cell.Blank, Cell.Text("Updated ${Civil.isoDate(today)}"))
        rows += listOf(
            Cell.Text("Written by the app from your entries. Each currency stays on its own, and nothing here is converted."),
        )
        rows += listOf<Cell>()

        currencies(transactions).forEach { currency ->
            rows += block(currency, month, thisYear, lastMonthRows.filter { it.month == lastMonth }, budgets[currency], today)
        }
        // Every row full width. A short row leaves whatever was to its right
        // the last time, and a blank is what clears a cell.
        return rows.map { row -> row + List(WIDTH - row.size) { Cell.Blank } }
    }

    private fun block(
        currency: Currency,
        month: YearMonth,
        year: List<SummaryRow>,
        lastMonth: List<SummaryRow>,
        budget: Budget?,
        today: Int,
    ): List<List<Cell>> {
        val mine = year.filter { it.currency == currency }
        fun spentIn(rows: List<SummaryRow>) =
            rows.filter { it.currency == currency }.sumMoney(currency) { it.spent }
        val spent = spentIn(mine.filter { it.month == month })
        val limit = budget?.takeIf { !it.deleted }?.limit
        val left = limit?.let { it - spent }
        val room = limit?.let {
            WidgetSnapshot(currency.code, spent.minor, it.minor, recordedDays = 0, monthEndDay = month.lastDay)
                .roomPerDay(today)
                ?.let { minor -> Money(minor, currency) }
        }

        val top = mine.filter { it.month == month && it.spent.minor > 0 }
            .sortedWith(compareByDescending<SummaryRow> { it.spent.minor }.thenBy { it.category ?: "￿" })
            .take(TOP)

        val out = mutableListOf<List<Cell>>()
        out += listOf(Cell.Text(currency.code))
        out += fact("Spent this month", spent)
        out += fact("Spent last month", spentIn(lastMonth))
        out += fact("Budget this month", limit)
        out += fact("Left this month", left?.takeIf { it.minor >= 0 })
        out += fact("Over this month by", left?.takeIf { it.minor < 0 }?.let { -it })
        out += fact("Room per day", room)
        out += fact("Spent this year", mine.sumMoney(currency) { it.spent })
        out += fact("Received this year", mine.sumMoney(currency) { it.received })
        out += listOf<Cell>()
        out += listOf(Cell.Text("Top this month"), Cell.Text("Spent"))
        repeat(TOP) { i ->
            val row = top.getOrNull(i)
            out += if (row == null) listOf() else listOf(Cell.Text(label(row.category)), number(row.spent))
        }
        out += listOf<Cell>()
        check(out.size == TREND_OFFSET) { "the trend has moved; charts would point at the wrong rows" }
        out += listOf(Cell.Text("Month"), Cell.Text("Spent"), Cell.Text("Received"))
        (1..12).forEach { m ->
            val ym = YearMonth(month.year, m)
            val rows = mine.filter { it.month == ym }
            // Months still to come are blank rather than zero: a zero is a
            // claim about a month, and nobody has lived these yet.
            out += if (ym > month) {
                listOf(Cell.Text(ym.toString()))
            } else {
                listOf(
                    Cell.Text(ym.toString()),
                    number(rows.sumMoney(currency) { it.spent }),
                    number(rows.sumMoney(currency) { it.received }),
                )
            }
        }
        while (out.size < BLOCK_ROWS) out += listOf<Cell>()
        return out
    }

    private fun fact(label: String, amount: Money?): List<Cell> =
        listOf(Cell.Text(label), amount?.let(::number) ?: Cell.Blank)

    private fun number(amount: Money): Cell = Cell.Number(amount.toPlainString())

    private fun label(category: String?): String =
        category?.let { Category.ofCode(it)?.label ?: it } ?: DerivedSummary.UNCATEGORISED

    /**
     * A column chart of one currency's months, spent beside received, anchored
     * to the right of its block. Its range is the block's trend and nothing
     * else, closed, so it never walks the grid (§2.7).
     */
    fun chart(sheetId: Int, blockIndex: Int, currency: Currency): EmbeddedChart {
        val header = trendHeaderRow(blockIndex)
        fun column(i: Int) = ChartData(
            ChartSourceRange(listOf(GridRange(sheetId, header, header + 13, i, i + 1))),
        )
        return EmbeddedChart(
            spec = ChartSpec(
                title = "${currency.code} by month",
                basicChart = BasicChartSpec(
                    axis = listOf(BasicChartAxis("BOTTOM_AXIS"), BasicChartAxis("LEFT_AXIS", currency.code)),
                    domains = listOf(BasicChartDomain(column(0))),
                    series = listOf(BasicChartSeries(column(1)), BasicChartSeries(column(2))),
                ),
            ),
            position = EmbeddedObjectPosition(
                OverlayPosition(
                    anchorCell = GridCoordinate(sheetId, HEADER_ROWS + blockIndex * BLOCK_ROWS, WIDTH + 1),
                    widthPixels = 560,
                    heightPixels = 320,
                ),
            ),
        )
    }

    /**
     * Which currencies already have the app's chart, in which tab. Charts are
     * added once: somebody who deletes one has said they do not want it, and
     * putting it back every sync would be the app arguing with them. The tab's
     * id is kept with the list because a person deleting the whole tab is a
     * fresh start, and the recreated tab gets its charts again.
     */
    data class Charted(val sheetId: Int, val currencies: Set<String>) {
        fun encode(): String = "$sheetId|" + currencies.sorted().joinToString(",")

        companion object {
            fun decode(raw: String?): Charted? {
                val (id, list) = raw?.split('|', limit = 2)?.takeIf { it.size == 2 } ?: return null
                return Charted(id.toIntOrNull() ?: return null, list.split(',').filter { it.isNotBlank() }.toSet())
            }
        }
    }
}
