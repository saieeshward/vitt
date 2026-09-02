package ie.shoonya.vitt.model

import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.sync.Event
import ie.shoonya.vitt.sync.EventLog
import ie.shoonya.vitt.sync.Hlc
import ie.shoonya.vitt.sync.TaggedValue

/**
 * A monthly spending limit for one currency.
 *
 * One budget per currency, never one budget across them: a combined limit would
 * need a rate to check against, and that rate would move (§0.6). Two currencies
 * means two limits, each meaningful on its own.
 *
 * A standing monthly limit rather than a figure per calendar month. Per-month
 * overrides are a plausible refinement, but the common case is one number the
 * user rarely changes, and storing a row per month would make an unedited budget
 * grow the log forever.
 *
 * The *limit* is user intent, so it syncs like any other field. What is never
 * synced is the rollup of spending against it — `PLAN.md:222` is explicit that a
 * derived aggregate written back is how balances come to disagree with the
 * transactions beneath them. [Ledger] computes that locally, every time.
 */
data class Budget(
    val currency: Currency,
    /** The monthly limit, always positive. */
    val limit: Money,
    val deleted: Boolean,
) {
    companion object {
        const val ENTITY = "budget"

        const val FIELD_LIMIT = "limit"
        const val FIELD_CURRENCY = "currency"

        /**
         * The currency code is the entity id.
         *
         * A natural key, so two devices setting a EUR budget converge on one row
         * instead of creating two that fight. There can only ever be one budget
         * per currency, which makes a synthetic id pure overhead.
         */
        fun idFor(currency: Currency): String = currency.code

        fun events(currency: Currency, limit: Money, issue: () -> Hlc): List<Event> {
            require(limit.currency == currency) {
                "a budget's limit cannot be in another currency"
            }
            require(limit.minor > 0) { "a budget must be a positive amount" }
            val id = idFor(currency)
            return listOf(
                Event(issue(), ENTITY, id, FIELD_CURRENCY, TaggedValue.Str(currency.code)),
                Event(issue(), ENTITY, id, FIELD_LIMIT, TaggedValue.Num(limit.minor)),
            )
        }

        /**
         * Projects a folded entity into a budget.
         *
         * Returns null when it cannot be read as one, or when the limit is not
         * positive — a hand-edited zero would otherwise render as a budget of
         * nothing, against which every expense is infinitely over.
         */
        fun from(key: EventLog.EntityKey, entity: EventLog.Entity): Budget? {
            if (key.entity != ENTITY) return null
            val currency = (entity.fields[FIELD_CURRENCY] as? TaggedValue.Str)
                ?.value?.let { Currency.ofCode(it) }
                ?: Currency.ofCode(key.entityId)
                ?: return null
            val minor = (entity.fields[FIELD_LIMIT] as? TaggedValue.Num)?.value ?: return null
            if (minor <= 0) return null
            return Budget(
                currency = currency,
                limit = Money(minor, currency),
                deleted = entity.deleted,
            )
        }
    }
}
