package ie.shoonya.vitt.widget

import ie.shoonya.vitt.model.Ledger
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money

/**
 * The little the home screen needs to know, written by the app and read by the
 * widget.
 *
 * A widget never opens the database. On Android it would mean touching the one
 * copy of someone's data from a broadcast at an arbitrary moment, and on iOS it
 * runs in a different process entirely. So the app publishes a snapshot whenever
 * the figures move and the widget renders that, which also means the widget
 * draws instantly instead of waiting on SQLite.
 *
 * One currency, because the widget is one small rectangle and `PLAN.md` §0.6
 * forbids the obvious way to fit several into it. There is no total across
 * currencies anywhere in this product and a widget is not where that rule gets
 * broken; it shows the currency most recently spent in and says which one it is.
 */
data class WidgetSnapshot(
    val currencyCode: String,
    val spentMinor: Long,
    /** Null when no budget is set. Never a negative dressed up as progress. */
    val budgetMinor: Long?,
    /** Days recorded in the current streak window, for the quiet line. */
    val recordedDays: Int,
    /**
     * The last day of the month, as days since the epoch.
     *
     * Carried so the widget can do the arithmetic itself at each midnight
     * without the app running. That is the whole point of the figure below:
     * a number that only moves when the user acts gives nobody a reason to
     * look, and a widget nobody looks at is not a feature.
     */
    val monthEndDay: Int,
) {
    /** 0f upwards, past 1f when over. Null with no budget. */
    fun pressure(): Float? {
        val limit = budgetMinor ?: return null
        if (limit == 0L) return null
        return spentMinor.toFloat() / limit.toFloat()
    }

    /**
     * What is left to spend per day for the rest of the month.
     *
     * The one figure on the widget worth glancing at, because of three
     * properties that "spent so far" does not have. It changes at every
     * midnight whether or not anybody opened the app. It goes *up* on a day
     * you spend nothing, so restraint is rewarded passively and without a
     * badge, a streak, or a word of congratulation. And it is read before
     * buying rather than after, which gives the glance a purpose.
     *
     * Framed as permission rather than as a verdict on purpose. `PLAN.md` §0
     * records the ostrich effect as the one robust finding behind this whole
     * layer: people avoid looking at financial information when they expect
     * bad news. A number that is safe to look at is the entire design.
     *
     * Null when there is no budget to divide, or when the month has run out.
     */
    fun roomPerDay(today: Int): Long? {
        val budget = budgetMinor ?: return null
        val left = budget - spentMinor
        // Over budget has no per-day allowance, and dressing a negative up as
        // one would be the app lying to make a number look friendlier.
        if (left <= 0L) return null
        // Inclusive of today: on the last day of the month there is one day
        // left, not zero, and dividing by zero would crash the home screen.
        val daysLeft = (monthEndDay - today + 1).coerceAtLeast(1)
        return left / daysLeft
    }

    /** Days remaining in the month, inclusive of today. Never below one. */
    fun daysLeft(today: Int): Int = (monthEndDay - today + 1).coerceAtLeast(1)

    /**
     * A flat, human-readable line. Deliberately not JSON.
     *
     * This crosses a process boundary into a widget that may be running an
     * older build of the app after an update, so it is parsed defensively and
     * kept trivial. A malformed or newer field yields no widget rather than a
     * crash on someone's home screen.
     */
    fun encode(): String =
        listOf(currencyCode, spentMinor, budgetMinor ?: -1L, recordedDays, monthEndDay)
            .joinToString("|")

    companion object {
        fun decode(raw: String?): WidgetSnapshot? {
            val parts = raw?.split('|') ?: return null
            if (parts.size != 5) return null
            val code = parts[0].takeIf { it.isNotBlank() } ?: return null
            val spent = parts[1].toLongOrNull() ?: return null
            val budget = parts[2].toLongOrNull() ?: return null
            val days = parts[3].toIntOrNull() ?: return null
            val monthEnd = parts[4].toIntOrNull() ?: return null
            return WidgetSnapshot(code, spent, budget.takeIf { it >= 0 }, days, monthEnd)
        }

        /**
         * Picks the currency to show.
         *
         * The one most recently spent in, falling back to the first the ledger
         * knows. Someone who has just paid an Indian bill wants to see rupees,
         * and the app cannot ask a widget which currency it meant.
         */
        fun from(
            ledgers: List<Ledger>,
            preferred: Currency?,
            recordedDays: Int,
            monthEndDay: Int,
        ): WidgetSnapshot? {
            val ledger = ledgers.firstOrNull { it.currency == preferred }
                ?: ledgers.firstOrNull()
                ?: return null
            return WidgetSnapshot(
                currencyCode = ledger.currency.code,
                spentMinor = ledger.spent.minor,
                budgetMinor = ledger.budget?.minor,
                recordedDays = recordedDays,
                monthEndDay = monthEndDay,
            )
        }
    }
}

/** Formats the snapshot's figure for display, without the widget knowing about [Money]. */
fun WidgetSnapshot.display(): String {
    val currency = Currency.ofCode(currencyCode) ?: return ""
    return Money(spentMinor, currency).displayUnsigned()
}

/** The per-day figure, formatted, or null when there is no allowance to show. */
fun WidgetSnapshot.perDayDisplay(today: Int): String? {
    val minor = roomPerDay(today) ?: return null
    val currency = Currency.ofCode(currencyCode) ?: return null
    return Money(minor, currency).displayUnsigned()
}
