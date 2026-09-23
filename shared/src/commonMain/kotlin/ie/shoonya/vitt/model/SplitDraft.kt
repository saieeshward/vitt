package ie.shoonya.vitt.model

import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.money.sumMoney

/**
 * A split being edited: the bill, who it is for, and any amounts typed by hand.
 *
 * The rule is Tricount's, and the one people expect from every split app: the
 * user is a row like everybody else, an amount somebody typed stays exactly
 * where they put it, and the rows nobody has touched share whatever is left,
 * equally. So a split is equal until a figure is typed, and typing one figure
 * never moves another figure somebody typed.
 *
 * An earlier version took every change out of the user's own share, on the
 * grounds that they paid. It was predictable only to its author: changing Bea
 * moved the user and nobody else, which is not what Splitwise or Tricount do
 * and not what anybody who has used either would guess.
 */
data class SplitDraft(
    val total: Money,
    /** The other people on it, sorted, which is the order every device agrees on. */
    val people: List<String>,
    /** Amounts typed by hand, by person; [YOU] for the user's own row. */
    val fixed: Map<String, Money> = emptyMap(),
) {
    /** Everybody on the split, the user first. */
    val everyone: List<String> get() = listOf(YOU) + people

    private val fixedSum: Money get() = fixed.values.sumMoney(total.currency) { it }

    /** What is left for the rows nobody typed into. */
    private val remaining: Money get() = total - fixedSum

    private val free: List<String> get() = everyone.filterNot { it in fixed }

    /**
     * Each row's amount. The untouched rows share [remaining] with the odd
     * cents going to the earliest rows, the user first: they paid, so the
     * rounding is theirs rather than a friend's.
     */
    val amounts: Map<String, Money>
        get() {
            val shared = if (free.isEmpty() || remaining.minor < 0) {
                free.associateWith { Money(0, total.currency) }
            } else {
                val parts = remaining.splitEvenly(free.size)
                free.withIndex().associate { (i, who) -> who to parts[i] }
            }
            return everyone.associateWith { fixed[it] ?: shared.getValue(it) }
        }

    val yours: Money get() = amounts.getValue(YOU)

    val others: Map<String, Money> get() = amounts - YOU

    /** How far the typed amounts overshoot the bill, or null. */
    val over: Money? get() = (fixedSum - total).takeIf { it.minor > 0 }

    /** Every row typed and still short of the bill: what is not yet given to anyone. */
    val unassigned: Money? get() = remaining.takeIf { free.isEmpty() && it.minor > 0 }

    /**
     * Adds up to the bill exactly. The user's own part may be nothing: a
     * ticket bought for a friend is a split the friend owes all of.
     */
    val isValid: Boolean
        get() = total.minor > 0 && over == null && unassigned == null &&
            amounts.values.sumOf { it.minor } == total.minor

    /** True when nothing was typed, so the split is simply equal. */
    val isEqual: Boolean get() = fixed.isEmpty()

    /** Somebody's amount typed by hand. */
    fun set(who: String, amount: Money): SplitDraft = copy(fixed = fixed + (who to amount))

    /** Back to sharing what is left. */
    fun unset(who: String): SplitDraft = copy(fixed = fixed - who)

    /** Everybody equal again. */
    fun equal(): SplitDraft = copy(fixed = emptyMap())

    /** A new bill; typed amounts stay where they were. */
    fun withTotal(bill: Money): SplitDraft = copy(total = bill)

    /** Somebody in or out. Out takes their typed amount with them. */
    fun toggle(person: String): SplitDraft {
        val who = person.trim().lowercase()
        if (who.isEmpty() || who == YOU) return this
        return if (who in people) {
            copy(people = people - who, fixed = fixed - who)
        } else {
            copy(people = (people + who).sorted())
        }
    }

    /**
     * What to store for the other people: their amounts, or null when the
     * split is equal. Equal needs no storage, because
     * [Transaction.shares] reads an unstored split as equal over the same
     * order this draft uses, and gets the same cents.
     */
    val othersToSave: Map<String, Money>? get() = if (isEqual) null else others

    companion object {
        /** The user's own row. Not an email or a name anybody could type. */
        const val YOU = "\u0000you"

        /** A new split: the bill, for the user and [people], equal. */
        fun of(total: Money, people: Collection<String> = emptyList()): SplitDraft =
            SplitDraft(total, people.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().sorted())

        /**
         * A saved split, ready to edit, reading as it was saved. Stored
         * amounts become typed rows; a split recorded with the user typing
         * only their own share keeps that share typed, and the others share
         * the rest as they always have.
         */
        fun of(split: Transaction): SplitDraft {
            val total = split.totalPaid?.abs() ?: split.amount.abs()
            val base = of(total, split.splitWith)
            val yours = split.amount.abs()
            return when {
                split.hasCustomShares -> base.copy(fixed = split.shares())
                base.yours == yours -> base
                else -> base.set(YOU, yours)
            }
        }
    }
}
