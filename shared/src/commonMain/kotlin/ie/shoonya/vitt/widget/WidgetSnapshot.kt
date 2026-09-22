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
) {
    /** 0f upwards, past 1f when over. Null with no budget. */
    fun pressure(): Float? {
        val limit = budgetMinor ?: return null
        if (limit == 0L) return null
        return spentMinor.toFloat() / limit.toFloat()
    }

    /**
     * A flat, human-readable line. Deliberately not JSON.
     *
     * This crosses a process boundary into a widget that may be running an
     * older build of the app after an update, so it is parsed defensively and
     * kept trivial. A malformed or newer field yields no widget rather than a
     * crash on someone's home screen.
     */
    fun encode(): String =
        listOf(currencyCode, spentMinor, budgetMinor ?: -1L, recordedDays).joinToString("|")

    companion object {
        fun decode(raw: String?): WidgetSnapshot? {
            val parts = raw?.split('|') ?: return null
            if (parts.size != 4) return null
            val code = parts[0].takeIf { it.isNotBlank() } ?: return null
            val spent = parts[1].toLongOrNull() ?: return null
            val budget = parts[2].toLongOrNull() ?: return null
            val days = parts[3].toIntOrNull() ?: return null
            return WidgetSnapshot(code, spent, budget.takeIf { it >= 0 }, days)
        }

        /**
         * Picks the currency to show.
         *
         * The one most recently spent in, falling back to the first the ledger
         * knows. Someone who has just paid an Indian bill wants to see rupees,
         * and the app cannot ask a widget which currency it meant.
         */
        fun from(ledgers: List<Ledger>, preferred: Currency?, recordedDays: Int): WidgetSnapshot? {
            val ledger = ledgers.firstOrNull { it.currency == preferred }
                ?: ledgers.firstOrNull()
                ?: return null
            return WidgetSnapshot(
                currencyCode = ledger.currency.code,
                spentMinor = ledger.spent.minor,
                budgetMinor = ledger.budget?.minor,
                recordedDays = recordedDays,
            )
        }
    }
}

/** Formats the snapshot's figure for display, without the widget knowing about [Money]. */
fun WidgetSnapshot.display(): String {
    val currency = Currency.ofCode(currencyCode) ?: return ""
    return Money(spentMinor, currency).displayUnsigned()
}
