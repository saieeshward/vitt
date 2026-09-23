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
import ie.shoonya.vitt.text.takeChars

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
    /**
     * True when this row came in from a file rather than from a person.
     *
     * The habit count is the only thing that reads it, and it reads it to stay
     * honest: a bank export carries a month of days with it, and counting those
     * as days the person turned up would hand out a thirty-day streak for one
     * tap. §5 asks the companion to reward presence, and an import is precisely
     * the absence of presence. Everything else — budgets, categories, reports,
     * balances — treats an imported row exactly like any other, because as a
     * record of money it is exactly like any other.
     *
     * Absent on every row written before this field existed, which reads as
     * false: those rows were all hand-logged, so false is also correct.
     */
    val imported: Boolean = false,
    /**
     * Each other person's share, when the split was entered per person, as
     * magnitudes. Empty for an equal split, which is the common case and costs
     * no storage. Trusted only while it still fits: see [hasCustomShares].
     */
    val customShares: Map<String, Money> = emptyMap(),
    /**
     * What each person has paid back, as a running total per person.
     *
     * Per person because a repayment is. The single [settled] total that came
     * first had to be divided equally, so Bea paying her whole share showed as
     * Bea and Cal each owing half of what was left.
     */
    val settledBy: Map<String, Money> = emptyMap(),
) {
    /** What the budget and the categories see: your share, not what you fronted. */
    val share: Money get() = amount

    /**
     * Whether this row counts in spent and received at all.
     *
     * The sign is the authority for direction; the category only decides
     * whether the row counts. A row a CSV import labelled "transfer" is the
     * user's own money changing pockets and moves neither total. Every sum in
     * the app goes through [isSpend] and [isIncome] so there is exactly one
     * place this rule lives.
     */
    val movesMoney: Boolean get() = !deleted && categoryOrNull?.movesMoney != false

    val isSpend: Boolean get() = movesMoney && amount.isOutflow
    val isIncome: Boolean get() = movesMoney && amount.isInflow

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
        // With people named, the sum of what each still owes, so one person
        // overpaying cannot cancel another's debt.
        if (splitWith.isNotEmpty()) {
            return splitWith.fold(Money(0, gross.currency)) { acc, who -> acc + outstandingFor(who)!! }
        }
        val left = gross - settled
        return if (left.minor < 0) Money(0, gross.currency) else left
    }

    /** True when a split has been fully repaid. */
    val isSettled: Boolean get() = outstanding()?.minor == 0L

    /**
     * True when the per-person amounts can be used: they name exactly the
     * people on the split and add up to exactly what those people owe.
     *
     * Anything else is read as an equal split. A person added or removed since
     * the amounts were set is the ordinary way to get here, and the amounts
     * that were right for the old group are wrong for the new one.
     */
    val hasCustomShares: Boolean get() {
        val owed = owed() ?: return false
        return customShares.isNotEmpty() &&
            customShares.keys == splitWith &&
            customShares.values.sumOf { it.minor } == owed.minor
    }

    /**
     * Each person's share of what the others owe, in the order every device
     * agrees on.
     *
     * Equal unless [hasCustomShares]. [Money.splitEvenly] gives the remainder
     * cents out deterministically over the sorted names, so the shares always
     * sum back to exactly what is owed and nobody's rounding invents money.
     */
    fun shares(): Map<String, Money> {
        val owed = owed() ?: return emptyMap()
        val people = splitWith.sorted()
        if (people.isEmpty()) return emptyMap()
        if (hasCustomShares) return people.associateWith { customShares.getValue(it) }
        val even = owed.splitEvenly(people.size)
        return people.withIndex().associate { (i, who) -> who to even[i] }
    }

    /** Everything that has come back, whichever way it was recorded. */
    val totalSettled: Money get() = settledBy.values.fold(settled) { acc, m -> acc + m }

    /**
     * What one participant still owes: their share, less what they paid back.
     *
     * A split recorded before repayments were per person has a single
     * [settled] total, which is spread equally over the people as it always
     * was, so an old entry reads exactly as it did.
     */
    fun outstandingFor(participant: String): Money? {
        val who = participant.trim().lowercase()
        val people = splitWith.sorted()
        val index = people.indexOf(who)
        if (index < 0) return null
        val share = shares().getValue(who)
        val legacy = if (settled.minor > 0) settled.splitEvenly(people.size)[index] else Money(0, share.currency)
        val left = share - legacy - (settledBy[who] ?: Money(0, share.currency))
        return if (left.minor < 0) Money(0, share.currency) else left
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
         * Written only when true, so a hand-logged row costs no extra event.
         * The common case is the daily one and it should stay the cheap one.
         */
        const val FIELD_IMPORTED = "imported"

        /** UTF-16 units. A note is a reminder, not a diary entry. */
        const val MAX_NOTE = 80

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
         * Every person's share in one field, unlike the participants.
         *
         * The shares are one fact: they have to add up to what is owed. Held
         * in one register, two devices editing them at once leave one whole
         * edit rather than half of each, which could add up to anything. An
         * empty value means equal.
         */
        const val FIELD_SHARES = "shares"

        /**
         * Repayments per person, one field each, for the opposite reason: two
         * phones recording Bea and Cal paying back at once must both land.
         * Absolute running totals, like [FIELD_SETTLED].
         */
        const val FIELD_SETTLED_BY_PREFIX = "settled_by:"

        fun settledByKey(participant: String): String =
            FIELD_SETTLED_BY_PREFIX + participant.trim().lowercase()

        /** One `minor<TAB>name` per line. A name is typed on one line, so it cannot hold a newline. */
        fun encodeShares(shares: Map<String, Money>): String =
            shares.entries.sortedBy { it.key }.joinToString("\n") { (who, m) -> "${m.minor}\t${who.trim().lowercase()}" }

        fun decodeShares(encoded: String?, currency: Currency): Map<String, Money> =
            encoded.orEmpty().lineSequence().mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) return@mapNotNull null
                val minor = line.substring(0, tab).toLongOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
                line.substring(tab + 1).takeIf { it.isNotBlank() }?.let { it to Money(minor, currency) }
            }.toMap()

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
            imported: Boolean = false,
            shares: Map<String, Money>? = null,
            issue: () -> Hlc,
        ): List<Event> = buildList {
            fun put(field: String, value: TaggedValue) =
                add(Event(issue(), ENTITY, id, field, value))

            put(FIELD_AMOUNT, TaggedValue.Num(amount.minor))
            put(FIELD_CURRENCY, TaggedValue.Str(amount.currency.code))
            put(FIELD_DAY, TaggedValue.Num(day.toLong()))
            // A blank merchant is no merchant. Written as "" it would fold to
            // an empty title on the Activity row and hide the note behind it.
            merchant?.takeIf { it.isNotBlank() }?.let { put(FIELD_MERCHANT, TaggedValue.Str(it)) }
            category?.let { put(FIELD_CATEGORY, TaggedValue.Str(it)) }
            categorySource?.let { put(FIELD_CATEGORY_SOURCE, TaggedValue.Str(it.code)) }
            accountId?.let { put(FIELD_ACCOUNT, TaggedValue.Str(it)) }
            totalPaid?.let { put(FIELD_TOTAL_PAID, TaggedValue.Num(it.minor)) }
            splitWith.forEach { put(splitKey(it), TaggedValue.Bool(true)) }
            note?.trim()?.takeIf { it.isNotEmpty() }?.let { put(FIELD_NOTE, TaggedValue.Str(it.takeChars(MAX_NOTE))) }
            if (imported) put(FIELD_IMPORTED, TaggedValue.Bool(true))
            shares?.let { put(FIELD_SHARES, TaggedValue.Str(encodeShares(it))) }
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
            // A row with no day would land in January 1970 and a zero row would
            // count as a recorded day while moving nothing. Both can only come
            // from a hand-edited or half-written sheet row, and neither is a
            // transaction: they disappear from the list rather than distort it.
            // The one real zero is the user's share of a bill they paid
            // entirely for somebody else, which has a bill beside it.
            val paid = (entity.fields[FIELD_TOTAL_PAID] as? TaggedValue.Num)?.value
            if (minor == 0L && (paid == null || paid == 0L)) return null
            val day = (entity.fields[FIELD_DAY] as? TaggedValue.Num)?.value ?: return null

            return Transaction(
                id = key.entityId,
                amount = Money(minor, currency),
                merchant = (entity.fields[FIELD_MERCHANT] as? TaggedValue.Str)?.value?.takeIf { it.isNotBlank() },
                category = (entity.fields[FIELD_CATEGORY] as? TaggedValue.Str)?.value,
                categorySource = (entity.fields[FIELD_CATEGORY_SOURCE] as? TaggedValue.Str)
                    ?.value?.let { CategorySource.ofCode(it) },
                accountId = (entity.fields[FIELD_ACCOUNT] as? TaggedValue.Str)?.value,
                day = day.toInt(),
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
                imported = (entity.fields[FIELD_IMPORTED] as? TaggedValue.Bool)?.value == true,
                customShares = decodeShares((entity.fields[FIELD_SHARES] as? TaggedValue.Str)?.value, currency),
                settledBy = entity.fields
                    .filterKeys { it.startsWith(FIELD_SETTLED_BY_PREFIX) }
                    .mapNotNull { (k, v) ->
                        (v as? TaggedValue.Num)?.value?.takeIf { it >= 0 }
                            ?.let { k.removePrefix(FIELD_SETTLED_BY_PREFIX) to Money(it, currency) }
                    }
                    .toMap(),
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
