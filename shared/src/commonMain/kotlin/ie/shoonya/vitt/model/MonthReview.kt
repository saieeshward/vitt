package ie.shoonya.vitt.model

import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.money.sumMoney
import ie.shoonya.vitt.time.YearMonth

/**
 * One currency's month, as a handful of facts worth a page each.
 *
 * The shape is Monzo's Year in Monzo, a short story rather than a report, and
 * it is here because a month told back to somebody is a reason to open the app
 * that does not depend on a streak or a nudge. The wording is not Monzo's. The
 * product rests on one finding, that people stop logging to avoid the number,
 * so every fact here is a fact about the month and never a verdict about the
 * person: "more than July" rather than "you overspent", a clear day counted
 * rather than a busy one scolded, and the budget as room left or a figure past
 * it, never as a failure.
 *
 * One currency at a time, like everything else. A review that summed euro and
 * rupees would be the one blended figure the app exists not to show.
 */
data class MonthReview(
    val month: YearMonth,
    val currency: Currency,
    /** Whether the month is over, which decides "in review" against "so far". */
    val complete: Boolean,
    val spent: Money,
    val received: Money,
    /** Entries of any kind in this currency, typed or imported. */
    val entries: Int,
    /** Days with at least one entry. */
    val daysRecorded: Int,
    /** Days so far with nothing going out. */
    val clearDays: Int,
    /**
     * Days of the month counted: from the first, or from the day logging
     * began if that was later, up to the end or to today.
     */
    val daysCounted: Int,
    /** The first day counted. Days before it were not recorded, so they say nothing. */
    val countedFrom: Int,
    /** Days of the month still ahead of today; zero once the month is complete. */
    val daysLeft: Int = 0,
    val topCategory: CategorySlice?,
    /** The top category's share of what went out, as a whole percentage. */
    val topShare: Int,
    /** The day the most went out, as days since the epoch, and how much. */
    val biggestDay: Pair<Int, Money>?,
    /** The merchant visited most often, with the count. */
    val regular: MerchantSlice?,
    /** This month's spending less last month's; null when last month had none. */
    val versusLast: Money?,
    val budget: Money?,
    /**
     * What went out with no category. Said beside the top category when it is
     * bigger, or "Groceries, 12%" reads as the story of a month that is mostly
     * uncategorised.
     */
    val uncategorised: Money = Money(0, currency),
) {
    /** What is left of the budget, or null when there is none or it is used up. */
    val room: Money? get() = budget?.let { it - spent }?.takeIf { it.minor > 0 }

    /** How far past the budget, or null. A figure, not a verdict. */
    val past: Money? get() = budget?.let { spent - it }?.takeIf { it.minor > 0 }

    /**
     * Within a twentieth either way, which is the same month as far as anybody
     * reading it is concerned; "€3 more than July" is noise dressed as news.
     */
    val aboutTheSame: Boolean
        get() = versusLast?.let { delta ->
            val last = spent - delta
            last.minor > 0 && kotlin.math.abs(delta.minor) * 20 <= last.minor
        } == true

    companion object {
        /**
         * Below this many entries a month has no story to tell, and a review
         * made of one coffee reads as a joke at the person's expense.
         */
        const val MIN_ENTRIES = 5

        fun of(
            transactions: List<Transaction>,
            currency: Currency,
            month: YearMonth,
            today: Int,
            budget: Money? = null,
            /**
             * The first day anything was recorded, in any currency. A day
             * before it was not a day with nothing out; it was a day before
             * the app, and counting it as clear would tell a new person a
             * fact about their month that nobody recorded.
             */
            trackedFrom: Int? = null,
        ): MonthReview? {
            val inMonth = transactions.filter { it.amount.currency == currency && !it.deleted && it.day in month }
            if (inMonth.size < MIN_ENTRIES) return null

            val complete = today > month.lastDay
            val daily = Insights.dailyTotals(transactions, currency, month, if (complete) month.lastDay else today)
            val from = maxOf(month.firstDay, trackedFrom ?: month.firstDay)
            val counted = daily.drop((from - month.firstDay).coerceIn(0, daily.size))
            val categories = Insights.byCategory(transactions, currency, month)
            val spent = categories.sumMoney(currency) { it.spent }
            val top = categories.firstOrNull { it.category != null }
            val lastSpent = Insights.byCategory(transactions, currency, month.previous()).sumMoney(currency) { it.spent }
            val biggest = daily.withIndex().maxByOrNull { it.value.minor }?.takeIf { it.value.minor > 0 }

            return MonthReview(
                month = month,
                currency = currency,
                complete = complete,
                spent = spent,
                received = inMonth.filter { it.isIncome }.sumMoney(currency) { it.amount },
                entries = inMonth.size,
                daysRecorded = inMonth.map { it.day }.distinct().size,
                clearDays = counted.count { it.minor == 0L },
                daysCounted = counted.size,
                countedFrom = from,
                daysLeft = if (complete) 0 else month.lastDay - today,
                topCategory = top,
                topShare = if (top == null || spent.minor == 0L) 0 else ((top.spent.minor * 100) / spent.minor).toInt(),
                biggestDay = biggest?.let { (i, m) -> (month.firstDay + i) to m },
                regular = Insights.topMerchants(transactions, currency, month, limit = 50)
                    .filter { it.count > 1 }
                    .maxWithOrNull(compareBy<MerchantSlice> { it.count }.thenBy { it.spent.minor }),
                versusLast = if (lastSpent.minor > 0) spent - lastSpent else null,
                budget = budget,
                uncategorised = categories.firstOrNull { it.category == null }?.spent ?: Money(0, currency),
            )
        }
    }
}
