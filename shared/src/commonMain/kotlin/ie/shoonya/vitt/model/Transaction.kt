package ie.shoonya.vitt.model

import ie.shoonya.vitt.capture.Category
import ie.shoonya.vitt.capture.CategorySource
import ie.shoonya.vitt.capture.MerchantName
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
    /**
     * Which tier of the categoriser produced [category].
     *
     * `PLAN.md` §6 calls this the single best debugging affordance in the
     * system: without it a wrong category is a mystery, and with it the fix is
     * obviously either the rule or the seed list. Null for rows that predate it
     * or arrived from a hand-edited sheet.
     */
    val categorySource: CategorySource?,
    val accountId: String?,
    /** Days since the Unix epoch — a date, with no time and no zone. */
    val day: Int,
    /** Set only when the expense was split; the full amount that was paid. */
    val totalPaid: Money?,
    /**
     * Who else was in on this, as the user labelled them — usually an email.
     *
     * Labels rather than identities: nothing here is verified, and nobody else's
     * app learns about it. Two-sided splits would need the shared-sheet
     * architecture in `docs/groups-plan.md`.
     */
    val splitWith: Set<String>,
    /**
     * How much of what was owed has actually come back.
     *
     * An absolute figure, not a running increment, so it is LWW-safe for the same
     * reason `amount` is (`PLAN.md:220`): two devices correcting a repayment to
     * the same value must not sum to double.
     */
    val settled: Money,
    /**
     * A few words the person typed, when the amount alone would not remind
     * them: "Birthday dinner", "Deposit back". Free text, never parsed, never a
     * categorisation signal; that is the merchant's job.
     */
    val note: String?,
    val deleted: Boolean,
) {
    /** What the budget and the categories see: your share, not what you fronted. */
    val share: Money get() = amount

    val isSplit: Boolean get() = totalPaid != null

    /** The stored category resolved against the locked taxonomy, if it is one. */
    val categoryOrNull: Category? get() = category?.let { Category.ofCode(it) }

    /**
     * The merchant as a person reads it: `TESCO STORES 3421 DUBLIN IE` → `Tesco`.
     *
     * Derived rather than stored, because the raw acquirer string is the truth
     * the bank sent and belongs in the sheet unaltered — while a screen showing
     * a terminal id is showing noise. Falls back to the raw string if nothing
     * recognisable survives, since that is still better than a blank row.
     */
    val merchantLabel: String? get() = merchant?.let { MerchantName.clean(it) ?: it }

    /** What someone else owes on this, in the currency it was incurred in. */
    fun owed(): Money? = totalPaid?.let { it.abs() - amount.abs() }

    /**
     * What is still owed after repayments, never below zero.
     *
     * Floored because an over-repayment is someone rounding up, not the user
     * owing them money back — turning it negative would quietly net against
     * another split.
     */
    fun outstanding(): Money? {
        val gross = owed() ?: return null
        val left = gross - settled
        return if (left.minor < 0) Money(0, gross.currency) else left
    }

    /** True when a split has been fully repaid. */
    val isSettled: Boolean get() = outstanding()?.minor == 0L

    /**
     * What one participant still owes, of a split shared between several.
     *
     * The outstanding amount divided equally, because per-participant shares are
     * not recorded — only the total the user fronted and their own share. Equal
     * division is an assumption, but the alternative is worse: attributing the
     * *whole* outstanding to each of them counts one debt twice, so three friends
     * owing €40 between them would read as €120.
     *
     * [Money.splitEvenly] distributes the remainder, so the shares always sum
     * back to exactly what is outstanding — nobody's rounding invents money.
     *
     * Participants are sorted so every device assigns the same remainder cent to
     * the same person.
     */
    fun outstandingFor(participant: String): Money? {
        val left = outstanding() ?: return null
        val who = participant.trim().lowercase()
        val index = splitWith.sorted().indexOf(who)
        if (index < 0) return null
        if (splitWith.isEmpty()) return left
        return left.splitEvenly(splitWith.size)[index]
    }

    companion object {
        const val ENTITY = "transaction"

        const val FIELD_AMOUNT = "amount"
        const val FIELD_CURRENCY = "currency"
        const val FIELD_MERCHANT = "merchant"
        const val FIELD_CATEGORY = "category"
        const val FIELD_CATEGORY_SOURCE = "categorized_by"
        const val FIELD_ACCOUNT = "account"
        const val FIELD_DAY = "day"
        const val FIELD_TOTAL_PAID = "total_paid"
        const val FIELD_SETTLED = "settled"
        const val FIELD_NOTE = "note"

        /**
         * Split participants are one field each, not one field holding a list.
         *
         * `PLAN.md:221` calls for an OR-Set here, because LWW on a JSON array
         * drops a concurrent add: two devices each adding a different person
         * would keep only one of them. Giving every participant its own field
         * gets that property out of the per-field LWW the project already has —
         * different people touch different fields and cannot collide, while a
         * concurrent add and remove of the *same* person resolves by HLC.
         */
        const val FIELD_SPLIT_PREFIX = "split_with:"

        /**
         * Participants are matched case-insensitively.
         *
         * Email addresses are case-insensitive in practice, and `Bob@x.com`
         * arriving from a phone while `bob@x.com` arrives from a laptop would
         * otherwise fork one person into two.
         */
        fun splitKey(participant: String): String =
            FIELD_SPLIT_PREFIX + participant.trim().lowercase()

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
            categorySource: CategorySource? = null,
            accountId: String?,
            totalPaid: Money?,
            splitWith: Set<String> = emptySet(),
            note: String? = null,
            issue: () -> Hlc,
        ): List<Event> = buildList {
            fun put(field: String, value: TaggedValue) =
                add(Event(issue(), ENTITY, id, field, value))

            put(FIELD_AMOUNT, TaggedValue.Num(amount.minor))
            put(FIELD_CURRENCY, TaggedValue.Str(amount.currency.code))
            put(FIELD_DAY, TaggedValue.Num(day.toLong()))
            merchant?.let { put(FIELD_MERCHANT, TaggedValue.Str(it)) }
            category?.let { put(FIELD_CATEGORY, TaggedValue.Str(it)) }
            categorySource?.let { put(FIELD_CATEGORY_SOURCE, TaggedValue.Str(it.code)) }
            accountId?.let { put(FIELD_ACCOUNT, TaggedValue.Str(it)) }
            totalPaid?.let { put(FIELD_TOTAL_PAID, TaggedValue.Num(it.minor)) }
            splitWith.forEach { put(splitKey(it), TaggedValue.Bool(true)) }
            note?.trim()?.takeIf { it.isNotEmpty() }?.let { put(FIELD_NOTE, TaggedValue.Str(it)) }
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
                categorySource = (entity.fields[FIELD_CATEGORY_SOURCE] as? TaggedValue.Str)
                    ?.value?.let { CategorySource.ofCode(it) },
                accountId = (entity.fields[FIELD_ACCOUNT] as? TaggedValue.Str)?.value,
                day = ((entity.fields[FIELD_DAY] as? TaggedValue.Num)?.value ?: 0L).toInt(),
                totalPaid = (entity.fields[FIELD_TOTAL_PAID] as? TaggedValue.Num)
                    ?.let { Money(it.value, currency) },
                splitWith = entity.fields
                    .filterKeys { it.startsWith(FIELD_SPLIT_PREFIX) }
                    .filterValues { (it as? TaggedValue.Bool)?.value == true }
                    .keys
                    .map { it.removePrefix(FIELD_SPLIT_PREFIX) }
                    .toSet(),
                settled = Money(
                    (entity.fields[FIELD_SETTLED] as? TaggedValue.Num)?.value ?: 0L,
                    currency,
                ),
                note = (entity.fields[FIELD_NOTE] as? TaggedValue.Str)?.value?.takeIf { it.isNotBlank() },
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
