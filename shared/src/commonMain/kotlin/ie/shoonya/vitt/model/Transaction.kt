package ie.shoonya.vitt.model

import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.sync.Event
import ie.shoonya.vitt.sync.EventLog
import ie.shoonya.vitt.sync.Hlc
import ie.shoonya.vitt.sync.TaggedValue

/**
 * A transaction, as the app displays it.
 *
 * Derived from the event log rather than stored: the log is the record, and this
 * is a projection of it. Nothing here is ever written directly.
 */
data class Transaction(
    val id: String,
    val amount: Money,
    val merchant: String?,
    val category: String?,
    val accountId: String?,
    /** Days since the Unix epoch — a date, with no time and no zone. */
    val day: Int,
    /** Set only when the expense was split; the full amount that was paid. */
    val totalPaid: Money?,
    val deleted: Boolean,
) {
    /** What the budget and the categories see: your share, not what you fronted. */
    val share: Money get() = amount

    val isSplit: Boolean get() = totalPaid != null

    /** What someone else owes on this, in the currency it was incurred in. */
    fun owed(): Money? = totalPaid?.let { it.abs() - amount.abs() }

    companion object {
        const val ENTITY = "transaction"

        const val FIELD_AMOUNT = "amount"
        const val FIELD_CURRENCY = "currency"
        const val FIELD_MERCHANT = "merchant"
        const val FIELD_CATEGORY = "category"
        const val FIELD_ACCOUNT = "account"
        const val FIELD_DAY = "day"
        const val FIELD_TOTAL_PAID = "total_paid"

        /**
         * Builds the events that record a new transaction.
         *
         * One event per field, because two devices editing different fields of
         * the same transaction should both win. The caller supplies the clock so
         * that minting and persisting cannot come apart.
         */
        fun events(
            id: String,
            amount: Money,
            day: Int,
            merchant: String?,
            category: String?,
            accountId: String?,
            totalPaid: Money?,
            issue: () -> Hlc,
        ): List<Event> = buildList {
            fun put(field: String, value: TaggedValue) =
                add(Event(issue(), ENTITY, id, field, value))

            put(FIELD_AMOUNT, TaggedValue.Num(amount.minor))
            put(FIELD_CURRENCY, TaggedValue.Str(amount.currency.code))
            put(FIELD_DAY, TaggedValue.Num(day.toLong()))
            merchant?.let { put(FIELD_MERCHANT, TaggedValue.Str(it)) }
            category?.let { put(FIELD_CATEGORY, TaggedValue.Str(it)) }
            accountId?.let { put(FIELD_ACCOUNT, TaggedValue.Str(it)) }
            totalPaid?.let { put(FIELD_TOTAL_PAID, TaggedValue.Num(it.minor)) }
        }

        /**
         * Projects a folded entity into a transaction.
         *
         * Returns null when the entity cannot be read as one — the currency is
         * unknown, the amount is missing. A row a person hand-edited into
         * nonsense should disappear from the list, not crash it.
         */
        fun from(key: EventLog.EntityKey, entity: EventLog.Entity): Transaction? {
            if (key.entity != ENTITY) return null
            val currency = (entity.fields[FIELD_CURRENCY] as? TaggedValue.Str)
                ?.value?.let { Currency.ofCode(it) } ?: return null
            val minor = (entity.fields[FIELD_AMOUNT] as? TaggedValue.Num)?.value ?: return null

            return Transaction(
                id = key.entityId,
                amount = Money(minor, currency),
                merchant = (entity.fields[FIELD_MERCHANT] as? TaggedValue.Str)?.value,
                category = (entity.fields[FIELD_CATEGORY] as? TaggedValue.Str)?.value,
                accountId = (entity.fields[FIELD_ACCOUNT] as? TaggedValue.Str)?.value,
                day = ((entity.fields[FIELD_DAY] as? TaggedValue.Num)?.value ?: 0L).toInt(),
                totalPaid = (entity.fields[FIELD_TOTAL_PAID] as? TaggedValue.Num)
                    ?.let { Money(it.value, currency) },
                deleted = entity.deleted,
            )
        }
    }
}

/**
 * One currency's ledger.
 *
 * Currencies are siblings, never summed. There is deliberately no field here for
 * a combined total — the type makes the product's one hard rule unrepresentable
 * rather than merely discouraged.
 */
data class Ledger(
    val currency: Currency,
    /** Assignment order, which decides the currency's colour. */
    val index: Int,
    val spent: Money,
    val received: Money,
    val budget: Money?,
) {
    val net: Money get() = received - spent

    /** Room left, or null when no budget is set — never a negative dressed up as progress. */
    fun remaining(): Money? = budget?.let { it - spent }

    /**
     * How much of the budget is used, 0f to 1f and beyond.
     *
     * Returned raw so the caller can show "over" without the model deciding
     * that over-budget is a failure state.
     */
    fun pressure(): Float? {
        val limit = budget ?: return null
        if (limit.minor == 0L) return null
        return spent.minor.toFloat() / limit.minor.toFloat()
    }
}
