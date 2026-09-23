package ie.shoonya.vitt.model

import ie.shoonya.vitt.capture.Categoriser
import ie.shoonya.vitt.capture.Category
import ie.shoonya.vitt.capture.MerchantName
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.time.Civil
import ie.shoonya.vitt.time.Period
import ie.shoonya.vitt.time.YearMonth

/**
 * The figures behind the charts.
 *
 * **Every function here takes a required [Currency].** That is the whole design:
 * `PLAN.md` §0.6 forbids any figure that sums across currencies, and a nullable
 * currency parameter would make such a figure expressible — someone would then
 * have to remember not to write it. A non-null parameter makes a cross-currency
 * chart unrepresentable instead of merely discouraged, the same trick
 * [Ledger] plays by having no field for a combined total.
 *
 * Pure functions over a list of transactions rather than methods on
 * [LedgerRepository], for two reasons: a chart screen wants three of these and
 * would otherwise fold the event log three times, and a pure function over a
 * list is testable without a database.
 *
 * Transfers are absent by construction — they are not [Transaction]s, so money
 * moved between the user's own accounts can never appear as spending in a chart.
 */
object Insights {

    /**
     * Spending per category, largest first.
     *
     * Outflows only. Income is not a small negative slice of a spending
     * breakdown — it is a different question, and mixing them makes the total
     * meaningless.
     */
    fun byCategory(
        transactions: List<Transaction>,
        currency: Currency,
        period: Period? = null,
    ): List<CategorySlice> = transactions
        .outflows(currency, period)
        .groupBy { it.categoryOrNull }
        .map { (category, rows) ->
            CategorySlice(
                category = category,
                spent = rows.total(currency),
                count = rows.size,
            )
        }
        // Largest first, then by label so two equal slices do not swap places
        // between reads. `null` sorts last whatever it is worth: "no category"
        // is a prompt to act, not a category competing for the top of the list.
        .sortedWith(
            compareBy<CategorySlice> { it.category == null }
                .thenByDescending { it.spent.minor }
                .thenBy { it.category?.label ?: "" },
        )

    /**
     * Spending and income across [count] consecutive periods ending at [period].
     *
     * Oldest first, because a chart is read left to right.
     *
     * Works at every grain for nothing, since stepping back is [Period.previous]
     * — the same operator the period control uses. There is deliberately no
     * all-time trend: a trend needs a grain, and all-time is the absence of a
     * period rather than a period with no bounds.
     *
     * Empty periods are included with a zero, not dropped. A month with no
     * spending is information — a gap in the bars that closes up would be a
     * chart that lies about its own axis.
     */
    fun trend(
        transactions: List<Transaction>,
        currency: Currency,
        period: Period,
        count: Int = 6,
    ): List<PeriodSlice> {
        require(count > 0) { "a trend needs at least one period" }
        val periods = ArrayDeque<Period>()
        var walk: Period = period
        repeat(count) {
            periods.addFirst(walk)
            walk = walk.previous()
        }
        return periods.map { p ->
            val rows = transactions.filter { it.amount.currency == currency && it.day in p }
            PeriodSlice(
                period = p,
                spent = rows.filter { it.isSpend }.total(currency),
                received = rows.filter { it.isIncome }
                    .fold(Money(0, currency)) { acc, t -> acc + t.amount },
            )
        }
    }

    /**
     * Where the money went, by merchant, largest first.
     *
     * Grouped by [Categoriser.applies] — the app's single definition of "the
     * same merchant" — rather than by raw [MerchantName.key] equality. That
     * distinction is the whole of this function's difficulty and it is not
     * cosmetic: `key` under-strips on purpose, so `TESCO DUBLIN` keys as `tesco`
     * while `TESCO NAAS 4471` keys as `tesco naas`, and grouping on the key
     * alone put one shop on the chart twice. It is the same drift that once had
     * the categoriser matching on prefixes while the past-entry search used
     * exact equality; reusing `applies` here is what stops a third copy of the
     * rule appearing.
     *
     * Each key collapses onto the shortest key in this data that `applies` to
     * it, which is the brand — so `tesco naas` folds into `tesco` when a plain
     * `tesco` row exists, and stands on its own when it does not. Nothing is
     * merged that the categoriser would not treat as one rule.
     *
     * Entries with no merchant are omitted rather than pooled: a "—" row at the
     * top of a merchant chart names nothing and cannot be acted on.
     */
    fun topMerchants(
        transactions: List<Transaction>,
        currency: Currency,
        period: Period? = null,
        limit: Int = 5,
    ): List<MerchantSlice> = transactions
        .outflows(currency, period)
        // `key` returns null when nothing recognisable survives normalisation,
        // and those rows drop out for the same reason a blank merchant does:
        // they cannot be labelled, so they cannot be a row on this chart.
        .mapNotNull { t -> t.merchant?.let { m -> MerchantName.key(m)?.to(t) } }
        .let { keyed ->
            val brands = brandsIn(keyed.map { it.first })
            keyed.groupBy({ brands.getValue(it.first) }, { it.first to it.second })
        }
        .map { (brand, rows) ->
            MerchantSlice(
                // The label of a row that keys as the brand itself, so a merged
                // group reads "Tesco" and not "Tesco Naas" — falling back to the
                // most recent row when every row carries a location.
                label = rows.firstOrNull { it.first == brand }?.second?.merchantLabel
                    ?: rows.maxByOrNull { it.second.day }?.second?.merchantLabel
                    ?: brand,
                spent = rows.map { it.second }.total(currency),
                count = rows.size,
            )
        }
        .sortedWith(compareByDescending<MerchantSlice> { it.spent.minor }.thenBy { it.label })
        .take(limit)

    /**
     * Maps every merchant key present onto the shortest key that [applies] to
     * it, which is the brand the rest of them are branches of.
     *
     * Shortest, then lexicographic, so the answer does not depend on the order
     * transactions happen to arrive in — two devices must draw the same chart.
     * Quadratic in the number of distinct merchants, which for one person's
     * ledger is a few hundred at most.
     */

    /**
     * This period against the one before it, category by category.
     *
     * The answer to "what changed?", which is the question a person actually
     * asks of a month — a total is only alarming or reassuring relative to the
     * last one. Movers are ordered by the size of the change, either direction,
     * so the top row is *the* category that moved (§5.6: "here's the one
     * category that moved"), not the biggest category.
     *
     * Both directions are reported in the same words. A fall is not praise and a
     * rise is not a verdict; each is a fact about where money went.
     *
     * Null when the period has no grain to step back along.
     */
    fun compare(
        transactions: List<Transaction>,
        currency: Currency,
        period: Period?,
    ): Comparison? {
        if (period == null) return null
        val before = period.previous()
        val now = byCategory(transactions, currency, period).associateBy { it.category }
        val then = byCategory(transactions, currency, before).associateBy { it.category }
        val zero = Money(0, currency)
        val movers = (now.keys + then.keys).map { category ->
            CategoryMove(
                category = category,
                now = now[category]?.spent ?: zero,
                before = then[category]?.spent ?: zero,
            )
        }
            .filter { it.delta.minor != 0L }
            .sortedWith(
                compareByDescending<CategoryMove> { it.delta.abs().minor }
                    .thenBy { it.category?.label ?: "" },
            )
        return Comparison(
            period = period,
            before = before,
            spent = now.values.fold(zero) { acc, s -> acc + s.spent },
            spentBefore = then.values.fold(zero) { acc, s -> acc + s.spent },
            movers = movers,
        )
    }

    /**
     * The merchants behind one category slice, for the drilldown.
     *
     * The same grouping as [topMerchants], filtered first, so a category's
     * merchants add up to the category's figure — the drilldown and the row it
     * opens from must never disagree.
     */
    fun merchantsIn(
        transactions: List<Transaction>,
        currency: Currency,
        period: Period?,
        category: Category?,
        limit: Int = 8,
    ): List<MerchantSlice> = topMerchants(
        transactions.filter { it.categoryOrNull == category },
        currency,
        period,
        limit,
    )

    /**
     * Where this month is likely to land, as a range.
     *
     * `PLAN.md` §5.3 governs every line: **always a range, never a point, and
     * never before day 7**. A projection on the 3rd is a statistically
     * meaningless extrapolation dressed as a warning, and a point estimate
     * invites the reader to treat noise as a verdict.
     *
     * The estimate is day-of-month weighted (§5.3 tier 2) when the user's own
     * history allows it: for each earlier month with spending, the share of
     * that month's total that had been spent by this day of the month gives
     * one estimate of where this month lands, and the range is the spread of
     * those estimates. That is what absorbs "rent on the 1st makes me look
     * doomed" — if rent always lands on the 1st, then by the 8th every past
     * month had also spent most of its total, and the ratio says so.
     *
     * With fewer than two usable months the naive linear rate is all there is,
     * widened by a fixed band and flagged [Projection.rough] so the screen can
     * say it is a rough guess rather than pretend otherwise.
     *
     * Baselined on the user's own months only, never on anyone else's (§5.2).
     *
     * @return null when the month is not the current one, it is too early, or
     *   nothing has been spent yet.
     */
    fun projectMonth(
        transactions: List<Transaction>,
        currency: Currency,
        month: YearMonth,
        today: Int,
        historyMonths: Int = 6,
    ): Projection? {
        if (today !in month) return null
        val (_, _, dayOfMonth) = Civil.fromDays(today)
        if (dayOfMonth < MIN_PROJECTION_DAY) return null

        val soFar = transactions.outflows(currency, month).filter { it.day <= today }.total(currency)
        if (soFar.minor == 0L) return null

        val daysInMonth = Civil.daysInMonth(month.year, month.month)
        val estimates = mutableListOf<Long>()
        val pastTotals = mutableListOf<Long>()
        var walk: YearMonth = month.previous()
        repeat(historyMonths) {
            val rows = transactions.outflows(currency, walk)
            val total = rows.total(currency).minor
            if (total > 0) {
                pastTotals += total
                // The same calendar day, or that month's last day if it is shorter.
                val cutoff = walk.firstDay + minOf(dayOfMonth, Civil.daysInMonth(walk.year, walk.month)) - 1
                val byThen = rows.filter { it.day <= cutoff }.total(currency).minor
                if (byThen > 0) estimates += soFar.minor * total / byThen
            }
            walk = walk.previous()
        }

        // With four or more months the single strangest one at each end is
        // dropped: a month with a holiday in it would otherwise stretch the
        // band until it said nothing. The band is still the user's own spread,
        // just not its two extremes.
        val kept = if (estimates.size >= 4) estimates.sorted().drop(1).dropLast(1) else estimates
        // What a month of the user's own usually comes to: the median of the
        // past months' totals. It is the baseline when there is no budget,
        // and it is the user's own, never anyone else's (§5.2).
        val typical = pastTotals.sorted().let { if (it.isEmpty()) null else Money(it[it.size / 2], currency) }

        return if (kept.size >= 2) {
            Projection(
                low = Money(kept.min(), currency),
                high = Money(kept.max(), currency),
                soFar = soFar,
                rough = false,
                typical = typical,
            )
        } else {
            val naive = soFar.minor * daysInMonth / dayOfMonth
            Projection(
                low = Money(naive * (100 - NAIVE_BAND_PERCENT) / 100, currency),
                high = Money(naive * (100 + NAIVE_BAND_PERCENT) / 100, currency),
                soFar = soFar,
                rough = true,
                typical = typical,
            )
        }
    }

    /**
     * Spending so far this month, day by day, as a running total.
     *
     * One entry per day from the 1st to [today] inclusive (or to the month's
     * end for a past month), in minor units. The shape of the month rather
     * than its total: a line that climbs early and flattens is rent; one that
     * climbs steadily is groceries; one that jumps at the end is a trip.
     */
    fun dailyCumulative(
        transactions: List<Transaction>,
        currency: Currency,
        month: YearMonth,
        today: Int,
    ): List<Long> {
        val last = minOf(today, month.lastDay)
        if (last < month.firstDay) return emptyList()
        val perDay = LongArray(last - month.firstDay + 1)
        transactions.outflows(currency, month).forEach { t ->
            if (t.day <= last) perDay[t.day - month.firstDay] += t.amount.abs().minor
        }
        var running = 0L
        return perDay.map { running += it; running }
    }

    /**
     * Spending by day of the week, Monday first.
     *
     * Totals, not averages, over [period]: the question this answers is "which
     * days does the money go on", and a total answers it directly.
     */
    fun byWeekday(
        transactions: List<Transaction>,
        currency: Currency,
        period: Period?,
    ): List<Money> {
        val totals = LongArray(7)
        transactions.outflows(currency, period).forEach { t ->
            totals[Civil.dayOfWeek(t.day)] += t.amount.abs().minor
        }
        return totals.map { Money(it, currency) }
    }

    /** §5.3: nothing is projected before the 7th. */
    const val MIN_PROJECTION_DAY = 7

    /** How far either side of the naive rate the fallback band reaches. */
    const val NAIVE_BAND_PERCENT = 15L

    private fun brandsIn(keys: List<String>): Map<String, String> {
        val distinct = keys.distinct()
        return distinct.associateWith { key ->
            distinct
                .filter { Categoriser.applies(it, key) }
                .minWithOrNull(compareBy<String> { it.length }.thenBy { it })
                ?: key
        }
    }

    private fun List<Transaction>.outflows(currency: Currency, period: Period?) = filter {
        it.amount.currency == currency &&
            it.isSpend &&
            (period == null || it.day in period)
    }

    /** Magnitudes, summed. Spending is reported as a positive amount out. */
    private fun List<Transaction>.total(currency: Currency) =
        fold(Money(0, currency)) { acc, t -> acc + t.amount.abs() }
}

/**
 * One category's share of a period's spending.
 *
 * [category] is null for everything the categoriser could not place, and that
 * slice is deliberately kept rather than dropped: with it the chart's slices sum
 * to exactly the figure on the ledger card, and without it the chart quietly
 * disagrees with the rest of the app.
 */
data class CategorySlice(
    val category: Category?,
    val spent: Money,
    val count: Int,
) {
    val label: String get() = category?.label ?: "No category"
}

/**
 * How one category moved between two consecutive periods.
 *
 * [delta] is now minus before: positive means more was spent this period.
 */
data class CategoryMove(
    val category: Category?,
    val now: Money,
    val before: Money,
) {
    val delta: Money get() = now - before
    val label: String get() = category?.label ?: "No category"
}

/** This period beside the last, with the categories that account for the change. */
data class Comparison(
    val period: Period,
    val before: Period,
    val spent: Money,
    val spentBefore: Money,
    /** Largest change first, either direction. */
    val movers: List<CategoryMove>,
) {
    val delta: Money get() = spent - spentBefore
}

/**
 * Where the month is likely to finish. Always a band; see [Insights.projectMonth].
 *
 * [rough] marks the fallback that had no history to lean on, so the screen can
 * say so rather than present a guess with the same face as an estimate.
 */
data class Projection(
    val low: Money,
    val high: Money,
    val soFar: Money,
    val rough: Boolean,
    /** The median of the user's own past months, or null with no history. */
    val typical: Money? = null,
) {
    /**
     * Where the month is heading, against the budget if there is one and
     * otherwise against the user's own usual month. The reading a person
     * actually wants — am I burning through it, fine, or well under — rather
     * than a number they would have to hold against another number.
     *
     * Null when there is nothing to read against.
     */
    fun pace(budget: Money?): Pace? {
        val against = (budget ?: typical)?.minor?.takeIf { it > 0 } ?: return null
        return when {
            // The whole band clears the mark: even the kindest estimate is over.
            low.minor > against -> Pace.AHEAD
            // The whole band sits well inside it.
            high.minor < against * UNDER_PERCENT / 100 -> Pace.UNDER
            else -> Pace.ON
        }
    }

    enum class Pace { UNDER, ON, AHEAD }

    companion object {
        /** Under this share of the mark, the month is running well under. */
        const val UNDER_PERCENT = 75L
    }
}

/** One period's spending and income, for a trend chart. */
data class PeriodSlice(
    val period: Period,
    val spent: Money,
    val received: Money,
)

/** One merchant's spending, keyed by the normalised name. */
data class MerchantSlice(
    val label: String,
    val spent: Money,
    val count: Int,
)

/**
 * The tallest bar in a set of slices, which is what the others are drawn against.
 *
 * Zero when nothing was spent, so a caller must handle that rather than divide
 * by it.
 */
fun List<PeriodSlice>.peakSpent(): Long = maxOfOrNull { it.spent.minor } ?: 0L

/** The share of [total] this slice is, 0f–1f, for a bar width. */
fun CategorySlice.shareOf(total: Money): Float {
    require(total.currency == spent.currency) { "a share must be of its own currency" }
    if (total.minor == 0L) return 0f
    return spent.minor.toFloat() / total.minor.toFloat()
}
