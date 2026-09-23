package ie.shoonya.vitt.model

import ie.shoonya.vitt.capture.Category
import ie.shoonya.vitt.money.Money

/**
 * Something this person logs again and again: the same coffee, the same bus
 * fare, the same lunch.
 *
 * The add screen offers these as one tap that fills in the whole entry, so a
 * repeat is two taps (the usual, then Save) instead of an amount, an account
 * and a category every morning. It is the lazy-user principle taken at its
 * word: people take the path of least effort, so the least-effort path has to
 * be the one that records the entry correctly, or the entry is not recorded.
 *
 * Nothing saves on its own. The tap fills the sheet and the person still
 * presses Save, because the one guess this app refuses to make is recording
 * money nobody confirmed.
 */
data class Usual(
    /** The note or merchant it was logged under; a figure alone is not recognisable. */
    val label: String,
    /** Signed, as recorded: the sign says expense or income. */
    val amount: Money,
    val category: Category?,
    val accountId: String?,
    /** Times it was logged in the window. */
    val count: Int,
)

object Usuals {
    /** Two months: long enough for a weekly habit to show, short enough to drop an old one. */
    const val WINDOW_DAYS = 60

    /** Once is a purchase; twice at the same price is a habit worth a shortcut. */
    const val MIN_REPEATS = 2

    /**
     * The repeats worth offering, most often first.
     *
     * Same label and same amount to the cent, because a usual is a price as
     * well as a place: Tesco at €41 and €12 is two shops, not one habit, and
     * filling in either would be a guess. Splits are left out, since the share
     * depends on who was there. The latest entry decides the category and the
     * account, so a correction made last week is the one that is offered.
     */
    fun of(transactions: List<Transaction>, today: Int, limit: Int = 4): List<Usual> =
        transactions.asSequence()
            .filter { it.movesMoney && !it.isSplit && it.day > today - WINDOW_DAYS && it.day <= today }
            .mapNotNull { t -> label(t)?.let { it to t } }
            .groupBy { (label, t) -> label.lowercase() to t.amount }
            .values
            .filter { it.size >= MIN_REPEATS }
            .map { group ->
                val (label, latest) = group.maxBy { it.second.day }
                Usual(label, latest.amount, latest.categoryOrNull, latest.accountId, group.size) to latest.day
            }
            .sortedWith(compareByDescending<Pair<Usual, Int>> { it.first.count }.thenByDescending { it.second })
            .take(limit)
            .map { it.first }

    private fun label(t: Transaction): String? =
        t.note?.trim()?.takeIf { it.isNotEmpty() } ?: t.merchantLabel?.trim()?.takeIf { it.isNotEmpty() }
}
