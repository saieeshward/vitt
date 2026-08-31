package ie.shoonya.vitt.model

import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.sync.Event
import ie.shoonya.vitt.sync.EventLog
import ie.shoonya.vitt.sync.Hlc
import ie.shoonya.vitt.sync.TaggedValue

/**
 * Where money sits: a current account, a wallet, a card.
 *
 * Single-currency by construction. A real multi-currency bank account is modelled
 * as one [Account] per currency, because the alternative is an account whose
 * balance is a sum across currencies — the one thing the product must never
 * compute (`PLAN.md` §0.6).
 *
 * Like [Transaction], this is a projection of the event log and never stored
 * directly.
 */
data class Account(
    val id: String,
    val name: String,
    val currency: Currency,
    val kind: AccountKind,
    /**
     * The balance before the first recorded transaction.
     *
     * Signed, so a credit card that already owed money starts negative. Tracking
     * had to begin somewhere, and pretending it began at zero makes every
     * balance wrong by a constant.
     */
    val opening: Money,
    /**
     * Archived accounts keep their history but leave the pickers.
     *
     * Distinct from deletion: a closed account's past transactions are still
     * true, so they must stay in the ledger.
     */
    val archived: Boolean,
    val deleted: Boolean,
) {
    companion object {
        const val ENTITY = "account"

        const val FIELD_NAME = "name"
        const val FIELD_CURRENCY = "currency"
        const val FIELD_KIND = "kind"
        const val FIELD_OPENING = "opening"
        const val FIELD_ARCHIVED = "archived"

        /**
         * Builds the events that record a new account.
         *
         * One event per field, matching [Transaction.events]: two devices
         * renaming and archiving the same account should both win.
         */
        fun events(
            id: String,
            name: String,
            currency: Currency,
            kind: AccountKind,
            opening: Money,
            issue: () -> Hlc,
        ): List<Event> {
            require(opening.currency == currency) {
                "an opening balance cannot be in another currency than its account"
            }
            return buildList {
                fun put(field: String, value: TaggedValue) =
                    add(Event(issue(), ENTITY, id, field, value))

                put(FIELD_NAME, TaggedValue.Str(name))
                put(FIELD_CURRENCY, TaggedValue.Str(currency.code))
                put(FIELD_KIND, TaggedValue.Str(kind.code))
                put(FIELD_OPENING, TaggedValue.Num(opening.minor))
            }
        }

        /**
         * Projects a folded entity into an account.
         *
         * Returns null when the entity cannot be read as one. A hand-edited row
         * that no longer names a currency should vanish from the pickers rather
         * than take the screen down with it.
         *
         * An unrecognised `kind` degrades to [AccountKind.OTHER] instead of
         * discarding the account — the name and currency are the parts the user
         * cannot reconstruct, and a newer version of the app writing a kind this
         * one has never heard of is expected, not corrupt.
         */
        fun from(key: EventLog.EntityKey, entity: EventLog.Entity): Account? {
            if (key.entity != ENTITY) return null
            val currency = (entity.fields[FIELD_CURRENCY] as? TaggedValue.Str)
                ?.value?.let { Currency.ofCode(it) } ?: return null
            val name = (entity.fields[FIELD_NAME] as? TaggedValue.Str)?.value ?: return null

            return Account(
                id = key.entityId,
                name = name,
                currency = currency,
                kind = (entity.fields[FIELD_KIND] as? TaggedValue.Str)
                    ?.value?.let { AccountKind.ofCode(it) } ?: AccountKind.OTHER,
                opening = Money(
                    (entity.fields[FIELD_OPENING] as? TaggedValue.Num)?.value ?: 0L,
                    currency,
                ),
                archived = (entity.fields[FIELD_ARCHIVED] as? TaggedValue.Bool)?.value ?: false,
                deleted = entity.deleted,
            )
        }
    }
}

/**
 * What kind of thing holds the money.
 *
 * Only affects presentation — an icon, and the sign convention a balance reads
 * naturally in. The codes are persisted into the log, so they are written out
 * rather than derived from [name], which refactoring would silently change.
 */
enum class AccountKind(val code: String) {
    CURRENT("current"),
    SAVINGS("savings"),
    CASH("cash"),

    /**
     * A card whose balance is what you owe.
     *
     * Still stored signed like every other account; the flag exists so a screen
     * can show "€120 owed" rather than "−€120" without inverting the arithmetic.
     */
    CREDIT("credit"),
    OTHER("other"),
    ;

    /** True when a negative balance is the normal state, not a warning. */
    val owesWhenNegative: Boolean get() = this == CREDIT

    companion object {
        fun ofCode(code: String): AccountKind? =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}

/**
 * An account together with the balance folded out of its transactions.
 *
 * Separate from [Account] because the balance is not a property of the account —
 * it is a function of the whole log, and caching it on the entity would invite
 * writing it back to the sheet as if it were a fact.
 */
data class AccountBalance(
    val account: Account,
    val balance: Money,
    val transactionCount: Int,
    /** Transfer legs touching this account — moves in or out, never spending. */
    val transferCount: Int = 0,
) {
    /**
     * The magnitude to display, with [owed] saying which way to read it.
     *
     * Kept as a pair rather than a pre-formatted string so the UI decides the
     * wording.
     */
    val owed: Boolean get() = account.kind.owesWhenNegative && balance.isOutflow

    val display: Money get() = if (owed) balance.abs() else balance
}
