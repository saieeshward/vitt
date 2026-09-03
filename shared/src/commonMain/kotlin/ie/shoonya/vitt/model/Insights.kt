package ie.shoonya.vitt.model

import ie.shoonya.vitt.capture.Categoriser
import ie.shoonya.vitt.capture.Category
import ie.shoonya.vitt.capture.MerchantName
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.time.Period

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
            val rows = transactions.filter {
                it.amount.currency == currency && it.day in p
            }
            PeriodSlice(
                period = p,
                spent = rows.filter { it.amount.isOutflow }.total(currency),
                received = rows.filter { it.amount.isInflow }
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
            it.amount.isOutflow &&
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
