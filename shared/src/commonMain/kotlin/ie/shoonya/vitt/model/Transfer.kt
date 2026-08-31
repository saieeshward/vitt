package ie.shoonya.vitt.model

import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.money.Rate
import ie.shoonya.vitt.sync.Event
import ie.shoonya.vitt.sync.EventLog
import ie.shoonya.vitt.sync.Hlc
import ie.shoonya.vitt.sync.TaggedValue

/**
 * Money moved between two accounts the user owns.
 *
 * Not income and not an expense — nothing entered or left the user's net worth,
 * it is simply somewhere else now. That distinction is the whole reason this is
 * its own entity: recorded as two ordinary transactions, a €500 move to a savings
 * account would show up as €500 of spending that never happened, blow the EUR
 * budget, and count as activity in the streak.
 *
 * Both legs are stored as observed magnitudes. When they are in different
 * currencies their quotient is the one rate the app ever records — a fact about a
 * transfer that happened, never a market estimate (`PLAN.md` §0.6).
 *
 * A projection of the event log, like [Transaction] and [Account]; never stored
 * directly.
 */
data class Transfer(
    val id: String,
    val fromAccountId: String,
    val toAccountId: String,
    /** Magnitude that left [fromAccountId]. Positive. */
    val sent: Money,
    /** Magnitude that arrived in [toAccountId]. Positive. */
    val received: Money,
    /** Days since the Unix epoch. */
    val day: Int,
    val note: String?,
    val deleted: Boolean,
) {
    val isCrossCurrency: Boolean get() = sent.currency != received.currency

    /**
     * The rate this transfer moved at, or null when both legs share a currency.
     *
     * Null rather than 1:1 because a same-currency transfer records no FX event,
     * and a stored rate of 1 would be indistinguishable from one that was
     * actually observed.
     */
    val rate: Rate?
        get() = if (!isCrossCurrency) null else Rate(
            from = sent.currency,
            to = received.currency,
            sentMinor = sent.minor,
            receivedMinor = received.minor,
        )

    /**
     * What the move cost, when that is knowable without inventing a rate.
     *
     * Only same-currency transfers can answer this: €500 sent and €497.50
     * arrived is unambiguously €2.50 of fees. Across currencies the fee is inside
     * the spread, and separating it would need a mid-market reference rate — an
     * outside estimate, which §0.6 does not allow. Null is the honest answer.
     */
    val fee: Money?
        get() = if (isCrossCurrency) null else sent - received

    /** The signed effect on one account's balance, or null if uninvolved. */
    fun effectOn(accountId: String): Money? = when (accountId) {
        fromAccountId -> -sent
        toAccountId -> received
        else -> null
    }

    companion object {
        const val ENTITY = "transfer"

        const val FIELD_FROM = "from_account"
        const val FIELD_TO = "to_account"
        const val FIELD_SENT = "sent"
        const val FIELD_SENT_CURRENCY = "sent_currency"
        const val FIELD_RECEIVED = "received"
        const val FIELD_RECEIVED_CURRENCY = "received_currency"
        const val FIELD_DAY = "day"
        const val FIELD_NOTE = "note"

        /** One event per field, matching the other entities, so concurrent edits merge. */
        fun events(
            id: String,
            fromAccountId: String,
            toAccountId: String,
            sent: Money,
            received: Money,
            day: Int,
            note: String?,
            issue: () -> Hlc,
        ): List<Event> {
            validate(fromAccountId, toAccountId, sent, received)
            return buildList {
                fun put(field: String, value: TaggedValue) =
                    add(Event(issue(), ENTITY, id, field, value))

                put(FIELD_FROM, TaggedValue.Str(fromAccountId))
                put(FIELD_TO, TaggedValue.Str(toAccountId))
                put(FIELD_SENT, TaggedValue.Num(sent.minor))
                put(FIELD_SENT_CURRENCY, TaggedValue.Str(sent.currency.code))
                put(FIELD_RECEIVED, TaggedValue.Num(received.minor))
                put(FIELD_RECEIVED_CURRENCY, TaggedValue.Str(received.currency.code))
                put(FIELD_DAY, TaggedValue.Num(day.toLong()))
                note?.let { put(FIELD_NOTE, TaggedValue.Str(it)) }
            }
        }

        internal fun validate(
            fromAccountId: String,
            toAccountId: String,
            sent: Money,
            received: Money,
        ) {
            require(fromAccountId != toAccountId) {
                "a transfer needs two different accounts"
            }
            // Magnitudes, not signed amounts: the direction is carried by the two
            // account fields. Allowing a negative would make a transfer that
            // silently runs backwards representable.
            require(sent.minor > 0) { "a transfer must send a positive amount" }
            require(received.minor > 0) { "a transfer must receive a positive amount" }
        }

        /**
         * Projects a folded entity into a transfer.
         *
         * Returns null when it cannot be read as one — a missing leg, an unknown
         * currency, or a hand-edited row whose two accounts are now the same.
         * Such a row disappears from the list rather than corrupting a balance.
         */
        fun from(key: EventLog.EntityKey, entity: EventLog.Entity): Transfer? {
            if (key.entity != ENTITY) return null
            fun str(f: String) = (entity.fields[f] as? TaggedValue.Str)?.value
            fun num(f: String) = (entity.fields[f] as? TaggedValue.Num)?.value

            val from = str(FIELD_FROM) ?: return null
            val to = str(FIELD_TO) ?: return null
            if (from == to) return null

            val sentCurrency = str(FIELD_SENT_CURRENCY)?.let { Currency.ofCode(it) } ?: return null
            val receivedCurrency =
                str(FIELD_RECEIVED_CURRENCY)?.let { Currency.ofCode(it) } ?: return null
            val sentMinor = num(FIELD_SENT) ?: return null
            val receivedMinor = num(FIELD_RECEIVED) ?: return null
            if (sentMinor <= 0 || receivedMinor <= 0) return null

            return Transfer(
                id = key.entityId,
                fromAccountId = from,
                toAccountId = to,
                sent = Money(sentMinor, sentCurrency),
                received = Money(receivedMinor, receivedCurrency),
                day = (num(FIELD_DAY) ?: 0L).toInt(),
                note = str(FIELD_NOTE),
                deleted = entity.deleted,
            )
        }
    }
}
