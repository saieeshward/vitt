package ie.shoonya.vitt.model

import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.time.YearMonth

/**
 * Something the user could do, that the companion can walk over to.
 *
 * The companion's job here is to *point*, in the literal sense: she goes and
 * stands by the thing. Gaze and position are the oldest attention cues there
 * are and they carry no judgement, which is the whole reason this exists as a
 * mechanic and a face does not. `PLAN.md` §5.6 rules out disappointment, and
 * `docs/pip-motion.md` names a character visibly let down by you as the most
 * expensive thing that could be on the home screen. So: she beckons, she never
 * sulks. Tapping her does the thing; ignoring her costs nothing and she gives up.
 */
enum class NudgeKind {
    /** Entries the categoriser could not place. The charts are wrong until they are. */
    REVIEW_CATEGORIES,

    /** A split with money still owed to the user. */
    SETTLE_SPLIT,

    /** A currency being spent in this month with no budget set. */
    SET_BUDGET,

    /** Nothing recorded today, for a user who has been recording. */
    RECORD_TODAY,

    /** Not connected to a spreadsheet after a week of real use. */
    CONNECT_SHEET,
}

data class Nudge(
    val kind: NudgeKind,
    /** How many items are behind it, for a count in the screen reader label. */
    val count: Int = 1,
    /** Which currency, for [NudgeKind.SET_BUDGET]. */
    val currency: Currency? = null,
) {
    /** The screen-reader label. The visible animal has no words at all. */
    val label: String get() = when (kind) {
        NudgeKind.REVIEW_CATEGORIES -> if (count == 1) "1 entry needs a category" else "$count entries need a category"
        NudgeKind.SETTLE_SPLIT -> if (count == 1) "1 split still open" else "$count splits still open"
        NudgeKind.SET_BUDGET -> "No budget for ${currency?.code ?: "this currency"} yet"
        NudgeKind.RECORD_TODAY -> "Nothing recorded today"
        NudgeKind.CONNECT_SHEET -> "Not backed up to a sheet"
    }
}

/** The things the companion may point at, most worth pointing at first. */
object Nudges {

    /**
     * What is pending, in priority order. Empty means she is free to wander.
     *
     * Priority is by what the omission costs the user: an uncategorised entry
     * makes every chart wrong; an open split is someone else's money; a missing
     * budget is a missing feature; the rest is gentle.
     *
     * Two things are deliberately *not* nudged. A blown budget: that is the
     * ostrich moment (§5.1), and a companion standing on the overspent card is
     * a reproach with fur on. And a long absence: non-logging can never read as
     * neglect (§5.2), so [NudgeKind.RECORD_TODAY] only fires for someone who was
     * recording this week and simply has not yet today.
     *
     * @param habitOn the §5.7 switch. Off means no habit nudges at all.
     * @param connected whether a spreadsheet is attached.
     * @param firstDay the day the first entry was made, or null for an empty ledger.
     */
    fun pending(
        transactions: List<Transaction>,
        budgets: Map<Currency, Budget>,
        today: Int,
        habitOn: Boolean,
        connected: Boolean,
        firstDay: Int?,
        /** Days marked as having nothing to record. They count as recorded. */
        noSpendDays: Set<Int> = emptySet(),
    ): List<Nudge> = buildList {
        val live = transactions.filterNot { it.deleted }

        val uncategorised = live.count { it.category == null && it.amount.isOutflow }
        if (uncategorised > 0) add(Nudge(NudgeKind.REVIEW_CATEGORIES, uncategorised))

        val open = live.count { it.isSplit && !it.isSettled }
        if (open > 0) add(Nudge(NudgeKind.SETTLE_SPLIT, open))

        // Only currencies spent in *this* month: a budget for a currency last
        // touched on a holiday two years ago is not a gap.
        val month = YearMonth.of(today)
        live.filter { it.day in month && it.amount.isOutflow }
            .map { it.amount.currency }
            .distinct()
            .filterNot { it in budgets }
            .forEach { add(Nudge(NudgeKind.SET_BUDGET, currency = it)) }

        if (habitOn) {
            val days = live.map { it.day }.toSet() + noSpendDays
            val recentlyActive = (1..7).any { (today - it) in days }
            if (recentlyActive && today !in days) add(Nudge(NudgeKind.RECORD_TODAY))
        }

        if (!connected && firstDay != null && today - firstDay >= CONNECT_AFTER_DAYS) {
            add(Nudge(NudgeKind.CONNECT_SHEET))
        }
    }

    /** A week of use before the sheet is mentioned; before that it is a sales pitch. */
    const val CONNECT_AFTER_DAYS = 7
}
