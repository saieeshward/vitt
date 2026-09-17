package ie.shoonya.vitt.model

import ie.shoonya.vitt.capture.Categorisation
import ie.shoonya.vitt.capture.Categoriser
import ie.shoonya.vitt.capture.Category
import ie.shoonya.vitt.capture.CategorySource
import ie.shoonya.vitt.capture.MerchantName
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.money.Rate
import ie.shoonya.vitt.sync.Event
import ie.shoonya.vitt.sync.EventLog
import ie.shoonya.vitt.sync.EventStore
import ie.shoonya.vitt.time.Period
import ie.shoonya.vitt.time.YearMonth
import ie.shoonya.vitt.sync.TaggedValue

/**
 * Reads the app's state out of the event log, and records new transactions into
 * it.
 *
 * The only writer of transactions. Screens call [record] and never touch the
 * event log, so minting a timestamp, appending the event and queueing it for the
 * sheet cannot come apart.
 */
class LedgerRepository(
    private val store: EventStore,
    private val now: () -> Long,
) {

    /**
     * Records a transaction locally and queues it for the sheet.
     *
     * @param amount signed: negative is money out. The caller decides the sign,
     * because the keypad only ever holds a magnitude.
     */
    fun record(
        id: String,
        amount: Money,
        day: Int,
        merchant: String? = null,
        category: String? = null,
        accountId: String? = null,
        totalPaid: Money? = null,
        splitWith: Set<String> = emptySet(),
        note: String? = null,
    ) {
        require(totalPaid == null || totalPaid.currency == amount.currency) {
            "a split cannot cross currencies"
        }
        require(splitWith.isEmpty() || totalPaid != null) {
            "naming who a expense was split with needs the total that was paid"
        }
        // An account this device has not seen yet is not an error: its event may
        // still be in flight from another device, and refusing would lose the
        // transaction outright. Only a *known* mismatch is a caller bug.
        if (accountId != null) {
            // Indexed lookup of this one account, not a fold of every event
            // ever written. Recording used to ask "what accounts exist" by
            // reading the whole log, which made the cost of adding a row grow
            // with the ledger and a bulk CSV import quadratic: measured at
            // 4.5ms per row at 250 rows and 13.5ms per row at 1,000.
            val account = store.foldEntity(Account.ENTITY, accountId)
                .firstNotNullOfOrNull { (key, entity) -> Account.from(key, entity) }
                ?.takeUnless { it.deleted }
            require(account == null || account.currency == amount.currency) {
                "a transaction cannot be recorded into an account of another currency"
            }
        }
        // Categorise when the caller did not. Capture is the daily path and
        // every required field is a future uninstall, so the tiers run here
        // rather than asking — and record which tier fired, so a wrong answer
        // can be explained instead of guessed at.
        val suggested = if (category == null) suggestCategory(merchant) else null
        // Store the canonical code, never a display label. `Category.ofCode`
        // reads both, so older rows still resolve — but writing two spellings
        // for one category would make the log's own history ambiguous.
        val stored = category?.let { Category.ofCode(it)?.code ?: it } ?: suggested?.category?.code
        val events = Transaction.events(
            id = id,
            amount = amount,
            day = day,
            merchant = merchant,
            category = stored,
            categorySource = when {
                category != null -> CategorySource.MANUAL
                else -> suggested?.source
            },
            accountId = accountId,
            totalPaid = totalPaid,
            splitWith = splitWith,
            note = note,
            issue = store::issue,
        )
        val at = now()
        events.forEach { store.append(it, at) }
    }

    /**
     * The categories this person actually uses, most used first.
     *
     * For the add screen's first row of chips. The locked taxonomy has fourteen
     * entries and most people live in five of them, so the row shows those and
     * folds the rest behind "More". Recency-weighted only by the window: a
     * category used once last week and one used daily two months ago count the
     * same, which keeps the row stable from one day to the next rather than
     * reshuffling under the thumb.
     */
    fun frequentCategories(
        today: Int,
        spending: Boolean = true,
        window: Int = 90,
        limit: Int = 6,
    ): List<Category> {
        val counts = transactions()
            .asSequence()
            .filter { it.day > today - window && it.day <= today }
            .mapNotNull { it.categoryOrNull }
            .filter { it.isSpending == spending }
            .groupingBy { it }
            .eachCount()
        val offered = Category.entries.filter { it.isSpending == spending }
        // Ties and the unused tail fall back to taxonomy order, so the row is
        // deterministic with no history at all.
        return offered.sortedByDescending { counts[it] ?: 0 }.take(limit)
    }

    /**
     * Records how much of a split has been repaid.
     *
     * Absolute, not incremental: pass the running total received, not the latest
     * instalment. `PLAN.md:220` is explicit that a counter CRDT is wrong here —
     * two devices correcting the same repayment must not sum to double.
     */
    fun settle(id: String, received: Money) {
        require(received.minor >= 0) { "a repayment cannot be negative" }
        store.append(
            Event(
                store.issue(),
                Transaction.ENTITY,
                id,
                Transaction.FIELD_SETTLED,
                TaggedValue.Num(received.minor),
            ),
            now(),
        )
    }

    /** Marks a split fully repaid, whatever it was owed. */
    fun settleInFull(id: String) {
        val owed = transactions().firstOrNull { it.id == id }?.owed() ?: return
        settle(id, owed)
    }

    /** Adds someone to a split. Their own field, so concurrent adds cannot collide. */
    fun addSplitParticipant(id: String, participant: String) =
        putSplitParticipant(id, participant, true)

    /** Removes someone from a split, resolved against a concurrent add by HLC. */
    fun removeSplitParticipant(id: String, participant: String) =
        putSplitParticipant(id, participant, false)

    private fun putSplitParticipant(id: String, participant: String, present: Boolean) {
        require(participant.isNotBlank()) { "a participant needs a label" }
        store.append(
            Event(
                store.issue(),
                Transaction.ENTITY,
                id,
                Transaction.splitKey(participant),
                TaggedValue.Bool(present),
            ),
            now(),
        )
    }

    /** Splits with anything still outstanding, newest first. */
    fun openSplits(): List<Transaction> = transactions()
        .filter { it.isSplit && !it.isSettled }

    /**
     * What one participant still owes, per currency, never netted across them.
     *
     * Per currency for the same reason every other balance is (§0.6), and because
     * Splitwise's free tier does the same: a single blended figure would move with
     * the market after the fact.
     *
     * A split shared with several people contributes only this person's share of
     * what is outstanding — see [Transaction.outstandingFor]. Attributing the
     * whole amount to each of them would count one debt once per participant.
     */
    fun outstandingBy(participant: String): Map<Currency, Money> {
        val key = Transaction.splitKey(participant).removePrefix(Transaction.FIELD_SPLIT_PREFIX)
        return openSplits()
            .filter { key in it.splitWith }
            .mapNotNull { t -> t.outstandingFor(key)?.let { t.amount.currency to it } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (currency, amounts) ->
                amounts.fold(Money(0, currency)) { acc, m -> acc + m }
            }
            .filterValues { it.minor > 0 }
    }

    /** Everyone who appears in an unsettled split, in a stable order. */
    fun openSplitParticipants(): List<String> =
        openSplits().flatMap { it.splitWith }.distinct().sorted()

    /** Soft-deletes. The row stays in the log so other devices learn about it. */
    fun delete(id: String) {
        store.append(
            ie.shoonya.vitt.sync.Event(
                store.issue(),
                Transaction.ENTITY,
                id,
                ie.shoonya.vitt.sync.EventLog.TOMBSTONE_FIELD,
                ie.shoonya.vitt.sync.TaggedValue.Bool(true),
            ),
            now(),
        )
    }

    /**
     * Records a new account and queues it for the sheet.
     *
     * The currency is fixed at creation and there is deliberately no way to
     * change it: the transactions already recorded against the account are
     * denominated in it, and re-denominating them would require a rate the app
     * refuses to invent (`PLAN.md` §0.6).
     */
    fun openAccount(
        id: String,
        name: String,
        currency: Currency,
        kind: AccountKind = AccountKind.CURRENT,
        opening: Money = Money(0, currency),
    ) {
        val events = Account.events(
            id = id,
            name = name,
            currency = currency,
            kind = kind,
            opening = opening,
            issue = store::issue,
        )
        val at = now()
        events.forEach { store.append(it, at) }
    }

    /** Renames an account. One field, so a concurrent archive still wins separately. */
    fun renameAccount(id: String, name: String) =
        putAccountField(id, Account.FIELD_NAME, TaggedValue.Str(name))

    /**
     * Archives or unarchives an account.
     *
     * Not a delete: the account leaves the pickers but its transactions stay in
     * the ledger, because they happened.
     */
    fun setAccountArchived(id: String, archived: Boolean) =
        putAccountField(id, Account.FIELD_ARCHIVED, TaggedValue.Bool(archived))

    private fun putAccountField(id: String, field: String, value: TaggedValue) {
        store.append(Event(store.issue(), Account.ENTITY, id, field, value), now())
    }

    /** Every live account, in creation order, archived ones last. */
    fun accounts(includeArchived: Boolean = false): List<Account> = store.foldOf(Account.ENTITY)
        .mapNotNull { (key, entity) -> Account.from(key, entity) }
        .filterNot { it.deleted }
        .filter { includeArchived || !it.archived }
        .sortedWith(compareBy<Account> { it.archived }.thenBy { it.id })

    /**
     * Each account with its balance, folded out of the transactions assigned to it.
     *
     * A transaction whose currency does not match its account is ignored rather
     * than coerced — that combination can only arrive from a hand-edited sheet
     * row, and adding it would produce a balance in no currency at all.
     * Transactions with no account are not attributed to any account.
     */
    fun accountBalances(includeArchived: Boolean = false): List<AccountBalance> {
        val byAccount = transactions()
            .filter { it.accountId != null }
            .groupBy { it.accountId!! }

        val moves = transfers()

        return accounts(includeArchived).map { account ->
            val mine = byAccount[account.id].orEmpty()
                .filter { it.amount.currency == account.currency }
            // Transfers move the balance even though they are not spending. A
            // leg whose currency does not match the account can only come from a
            // hand-edited row, and adding it would produce a balance in no
            // currency at all.
            val legs = moves.mapNotNull { it.effectOn(account.id) }
                .filter { it.currency == account.currency }
            AccountBalance(
                account = account,
                balance = (mine.map { it.amount } + legs)
                    .fold(account.opening) { acc, m -> acc + m },
                transactionCount = mine.size,
                transferCount = legs.size,
            )
        }
    }

    /**
     * Transactions not assigned to any account.
     *
     * Surfaced rather than hidden: share-sheet and CSV capture cannot always tell
     * which account paid, and an unassigned pile the user never sees is how a
     * balance quietly stops matching the bank.
     */
    fun unassigned(): List<Transaction> = transactions().filter { it.accountId == null }

    /**
     * Every rule the user has taught, keyed by normalised merchant.
     */
    fun categoryRules(): Map<String, Category> = store.foldOf(CategoryRule.ENTITY)
        .mapNotNull { (key, entity) -> CategoryRule.from(key, entity) }
        .filterNot { it.deleted }
        .associate { it.merchantKey to it.category }

    /** A categoriser loaded with this user's own rules ahead of the seeds. */
    fun categoriser(): Categoriser = Categoriser(categoryRules())

    /**
     * Suggests a category for a merchant, with the tier that produced it.
     *
     * Null means ask — §6's third tier. A wrong guess is worse than an empty
     * field, because it distorts a budget nobody thinks to check.
     */
    fun suggestCategory(merchant: String?): Categorisation? = categoriser().categorise(merchant)

    /**
     * Sets a transaction's category and, when it has a merchant, teaches the rule.
     *
     * The teaching is the point: §6's third tier is *ask, then write a tier-1
     * rule*, so a correction made once should never need making again. Recorded
     * as [CategorySource.MANUAL] on this transaction — the user chose it here,
     * whatever fires on the next one.
     */
    /**
     * Past entries from the same merchant that a new rule could safely restate.
     *
     * Excludes anything the user categorised by hand: a rule learned today must
     * not overwrite a decision they made deliberately last month. Only rows a
     * tier guessed at are candidates.
     */
    fun pastMatching(id: String): List<Transaction> {
        val all = transactions()
        val subject = all.firstOrNull { it.id == id } ?: return emptyList()
        val key = subject.merchant?.let { MerchantName.key(it) } ?: return emptyList()
        return all.filter { other ->
            other.id != id &&
                other.categorySource != CategorySource.MANUAL &&
                other.merchant
                    ?.let { MerchantName.key(it) }
                    ?.let { Categoriser.applies(key, it) } == true
        }
    }

    fun categorise(
        id: String,
        category: Category,
        teach: Boolean = true,
        /**
         * Restate the category on past entries from the same merchant.
         *
         * Off by default, because rewriting history should be asked for. But a
         * rule that leaves visibly wrong rows on the same screen reads as broken,
         * so the UI offers it whenever there is anything to apply it to.
         */
        applyToPast: Boolean = false,
    ) {
        val at = now()
        val transaction = transactions().firstOrNull { it.id == id }
        store.append(
            Event(
                store.issue(),
                Transaction.ENTITY,
                id,
                Transaction.FIELD_CATEGORY,
                TaggedValue.Str(category.code),
            ),
            at,
        )
        store.append(
            Event(
                store.issue(),
                Transaction.ENTITY,
                id,
                Transaction.FIELD_CATEGORY_SOURCE,
                TaggedValue.Str(CategorySource.MANUAL.code),
            ),
            at,
        )
        if (applyToPast) {
            // Marked LEARNED rather than MANUAL: the user chose it once, on the
            // entry they were looking at, and the rule is what reached these.
            pastMatching(id).forEach { past ->
                store.append(
                    Event(
                        store.issue(), Transaction.ENTITY, past.id,
                        Transaction.FIELD_CATEGORY, TaggedValue.Str(category.code),
                    ),
                    at,
                )
                store.append(
                    Event(
                        store.issue(), Transaction.ENTITY, past.id,
                        Transaction.FIELD_CATEGORY_SOURCE,
                        TaggedValue.Str(CategorySource.LEARNED.code),
                    ),
                    at,
                )
            }
        }

        if (!teach) return
        transaction?.merchant?.let { merchant ->
            CategoryRule.events(merchant, category, store::issue)
                ?.forEach { store.append(it, at) }
        }
    }

    /** Forgets a learned rule, so the merchant falls back to the seeds. */
    fun forgetCategoryRule(merchantKey: String) {
        store.append(
            Event(
                store.issue(),
                CategoryRule.ENTITY,
                CategoryRule.idFor(merchantKey),
                EventLog.TOMBSTONE_FIELD,
                TaggedValue.Bool(true),
            ),
            now(),
        )
    }

    /** Every preference the user has explicitly set. */
    fun preferences(): Map<String, Boolean> = store.foldOf(Preference.ENTITY)
        .mapNotNull { (key, entity) -> Preference.from(key, entity) }
        .filterNot { it.deleted }
        .associate { it.key to it.enabled }

    /**
     * Whether the gamification layer runs.
     *
     * Unset means the default, not off — an untouched install should behave as
     * designed, and the switch is about being able to decline rather than about
     * having to opt in.
     */
    fun gamificationEnabled(): Boolean =
        preferences()[Preference.GAMIFICATION] ?: Preference.GAMIFICATION_DEFAULT

    fun setGamificationEnabled(enabled: Boolean) =
        setPreference(Preference.GAMIFICATION, enabled)

    /**
     * Every named choice the user has explicitly made.
     *
     * Values are returned exactly as stored, including one this build does not
     * recognise. See [Choice] for why that is deliberate.
     */
    fun choices(): Map<String, String> = store.foldOf(Choice.ENTITY)
        .mapNotNull { (key, entity) -> Choice.from(key, entity) }
        .filterNot { it.deleted }
        .associate { it.key to it.value }

    /** One choice, or null when the user has not made it. Null means the default. */
    fun choice(key: String): String? = choices()[key]

    fun setChoice(key: String, value: String) {
        val at = now()
        Choice.events(key, value, store::issue).forEach { store.append(it, at) }
    }

    /** Forgets a choice, so it falls back to the default on every device. */
    fun clearChoice(key: String) {
        store.append(
            Event(
                store.issue(),
                Choice.ENTITY,
                key,
                EventLog.TOMBSTONE_FIELD,
                TaggedValue.Bool(true),
            ),
            now(),
        )
    }

    fun setPreference(key: String, enabled: Boolean) {
        val at = now()
        Preference.events(key, enabled, store::issue).forEach { store.append(it, at) }
    }

    /** Every live transaction, newest first. */
    fun transactions(): List<Transaction> = store.foldOf(Transaction.ENTITY)
        .mapNotNull { (key, entity) -> Transaction.from(key, entity) }
        .filterNot { it.deleted }
        .sortedWith(compareByDescending<Transaction> { it.day }.thenByDescending { it.id })

    /**
     * One ledger per currency the user actually has, in a stable order.
     *
     * @param period restricts the spent and received figures to one stretch of
     * days. Null totals all of history. The budget figure is filled in only for
     * a [YearMonth], because that is the only grain a monthly limit means
     * anything at.
     *
     * Order is by first appearance rather than by size, so a currency's colour
     * does not change under the user when they spend more in another one.
     */
    fun ledgers(period: Period? = null): List<Ledger> {
        val all = transactions()
        val limits = budgets()
        // True first appearance: oldest day first, and within a day the order
        // the rows were written. Reversing the display list instead made the
        // order depend on how ids happened to sort, so a currency's colour was
        // effectively arbitrary.
        val order = all
            .sortedWith(compareBy<Transaction> { it.day }.thenBy { it.id })
            .map { it.amount.currency }
            .distinct()

        return order.mapIndexed { index, currency ->
            // Order comes from all of history so a currency's colour never moves,
            // but the amounts are the period's — the card says "out this month"
            // and has to mean it.
            val forCurrency = all
                .filter { it.amount.currency == currency }
                .filter { period == null || it.day in period }
            Ledger(
                currency = currency,
                index = index,
                spent = forCurrency.filter { it.amount.isOutflow }
                    .fold(Money(0, currency)) { acc, t -> acc + t.amount.abs() },
                received = forCurrency.filter { it.amount.isInflow }
                    .fold(Money(0, currency)) { acc, t -> acc + t.amount },
                // Only at month grain. A monthly limit spread over a week is an
                // invented number, and wrong in a predictable direction: rent
                // lands on the 1st, so week one always looks catastrophic and
                // week four always looks virtuous. §5.3 makes the same point
                // about naive projection being biased high when rent has landed,
                // and §5.1's test is whether the app is cheaper to open on a bad
                // day — a fake weekly overspend fails it badly.
                budget = if (period is YearMonth) limits[currency]?.limit else null,
            )
        }
    }

    /**
     * Sets a currency's monthly limit, replacing any previous one.
     *
     * Keyed by currency, so this is an edit rather than an insert however many
     * times it is called or from how many devices.
     */
    fun setBudget(currency: Currency, limit: Money) {
        val events = Budget.events(currency, limit, store::issue)
        val at = now()
        events.forEach { store.append(it, at) }
    }

    /** Removes a currency's budget. Soft, so other devices learn it is gone. */
    fun clearBudget(currency: Currency) {
        store.append(
            Event(
                store.issue(),
                Budget.ENTITY,
                Budget.idFor(currency),
                EventLog.TOMBSTONE_FIELD,
                TaggedValue.Bool(true),
            ),
            now(),
        )
    }

    /** Every live budget, by currency. */
    fun budgets(): Map<Currency, Budget> = store.foldOf(Budget.ENTITY)
        .mapNotNull { (key, entity) -> Budget.from(key, entity) }
        .filterNot { it.deleted }
        .associateBy { it.currency }

    /**
     * Records money moved between two of the user's own accounts.
     *
     * Both amounts are magnitudes; direction comes from the two account ids. When
     * they differ in currency their quotient becomes the transfer's locked rate —
     * observed, not fetched (§0.6).
     *
     * @param sent what left [fromAccountId], positive.
     * @param received what arrived in [toAccountId], positive. Equal to [sent]
     * for a clean same-currency move; smaller when a fee was taken.
     */
    fun transfer(
        id: String,
        fromAccountId: String,
        toAccountId: String,
        sent: Money,
        received: Money,
        day: Int,
        note: String? = null,
    ) {
        Transfer.validate(fromAccountId, toAccountId, sent, received)
        // As with `record`, an account this device has not seen yet is not an
        // error — its event may still be in flight. Only a known mismatch is a
        // caller bug, and re-denominating a leg would need a rate we refuse to
        // invent.
        val known = accounts(includeArchived = true).associateBy { it.id }
        known[fromAccountId]?.let {
            require(it.currency == sent.currency) {
                "the sent leg must be in the source account's currency"
            }
        }
        known[toAccountId]?.let {
            require(it.currency == received.currency) {
                "the received leg must be in the destination account's currency"
            }
        }

        val events = Transfer.events(
            id = id,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            sent = sent,
            received = received,
            day = day,
            note = note,
            issue = store::issue,
        )
        val at = now()
        events.forEach { store.append(it, at) }
    }

    /** Soft-deletes a transfer, so other devices learn the move did not happen. */
    fun deleteTransfer(id: String) {
        store.append(
            Event(
                store.issue(),
                Transfer.ENTITY,
                id,
                EventLog.TOMBSTONE_FIELD,
                TaggedValue.Bool(true),
            ),
            now(),
        )
    }

    /** Every live transfer, newest first. */
    fun transfers(): List<Transfer> = store.foldOf(Transfer.ENTITY)
        .mapNotNull { (key, entity) -> Transfer.from(key, entity) }
        .filterNot { it.deleted }
        .sortedWith(compareByDescending<Transfer> { it.day }.thenByDescending { it.id })

    /**
     * Every rate this user's own money actually moved at, newest first.
     *
     * The app's complete FX record. Deliberately not a rate *table*: there is no
     * entry for a pair the user never transferred, and no interpolation between
     * the ones they did.
     */
    fun observedRates(): List<Pair<Int, Rate>> = transfers()
        .filter { it.isCrossCurrency }
        .mapNotNull { t -> t.rate?.let { t.day to it } }

    /** Transactions grouped by day, newest day first, for the activity list. */
    fun byDay(
        /** Restrict to one stretch of days. Null browses everything. */
        period: Period? = null,
        /** Restrict to one currency. Null shows every currency, never summed. */
        currency: Currency? = null,
        /**
         * Only entries with no category.
         *
         * §6.1 calls for a review queue for anything the tiers could not place.
         * Without one the third tier is theoretical: an uncategorised entry is
         * findable only by scrolling past everything else.
         */
        needingCategory: Boolean = false,
    ): List<Pair<Int, List<Transaction>>> = transactions()
        .filter { period == null || it.day in period }
        .filter { currency == null || it.amount.currency == currency }
        .filter { !needingCategory || it.category == null }
        .groupBy { it.day }
        .toList()
        .sortedByDescending { it.first }

    /**
     * Months that have at least one transaction, newest first.
     *
     * Lets a month stepper stop at the ends of the data rather than walking into
     * empty years.
     */
    fun monthsWithActivity(): List<YearMonth> = transactions()
        .map { YearMonth.of(it.day) }
        .distinct()
        .sortedDescending()

    /** How many entries the tiers could not categorise. */
    fun needingCategoryCount(period: Period? = null): Int = transactions()
        .filter { period == null || it.day in period }
        .count { it.category == null }

    /**
     * The span of days that have anything in them, or null when nothing does.
     *
     * Lets a period stepper stop at the edges of the data at any grain, without
     * enumerating every week or day — which would have to cope with gaps.
     */
    fun activityDayRange(): IntRange? {
        val days = transactions().map { it.day }
        val min = days.minOrNull() ?: return null
        return min..days.max()
    }

    /** Unsettled amounts owed to the user, per currency — never netted across them. */
    fun owed(): Map<Currency, Money> = transactions()
        .mapNotNull { t -> t.outstanding()?.let { t.amount.currency to it } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (currency, amounts) ->
            amounts.fold(Money(0, currency)) { acc, m -> acc + m }
        }
        .filterValues { it.minor > 0 }

    /**
     * Days on which anything was recorded, within the last [window] days.
     *
     * The streak counts days *recorded*, not days under budget: recording is the
     * only habit the app can honestly ask for, and rewarding thrift punishes the
     * month someone flew home for a funeral.
     */
    fun daysRecorded(today: Int, window: Int = 30): Int = recordedDays(today, window).size

    /**
     * Which of the last [window] days had anything recorded, as days since the
     * epoch. A day marked "nothing spent" counts: the habit is *looking*, and
     * saying there was nothing is looking.
     */
    fun recordedDays(today: Int, window: Int = 30): Set<Int> =
        allRecordedDays().filter { it > today - window && it <= today }.toSet()

    private fun allRecordedDays(): Set<Int> = transactions().map { it.day }.toSet() + noSpendDays()

    /**
     * Marks a day as one with nothing to record.
     *
     * The answer to "what about days I spend nothing?" A blank day is
     * indistinguishable from a forgotten one, and §5.2 forbids reading the
     * blank as bad — so the user can say which it was, in one tap, and the
     * day counts as observed. It is a record of attention, not of money: no
     * amount, no currency, nothing a budget sees.
     */
    fun markNothingSpent(day: Int) {
        val at = now()
        store.append(
            Event(store.issue(), NoSpend.ENTITY, day.toString(), NoSpend.FIELD_MARKED, TaggedValue.Bool(true)),
            at,
        )
    }

    fun noSpendDays(): Set<Int> = store.foldOf(NoSpend.ENTITY)
        .filter { (_, entity) -> (entity.fields[NoSpend.FIELD_MARKED] as? TaggedValue.Bool)?.value == true }
        .keys.mapNotNull { it.entityId.toIntOrNull() }
        .toSet()

    /** The most recent day each currency was recorded in. A fact, not a score. */
    fun lastRecordedByCurrency(): Map<Currency, Int> =
        transactions().groupBy { it.amount.currency }.mapValues { (_, rows) -> rows.maxOf { it.day } }

    /** Days recorded in the window, per currency. Never summed across them. */
    fun recordedDaysByCurrency(today: Int, window: Int = 30): Map<Currency, Int> =
        transactions()
            .filter { it.day > today - window && it.day <= today }
            .groupBy { it.amount.currency }
            .mapValues { (_, rows) -> rows.map { it.day }.distinct().size }

    /**
     * Days recorded in each of the last [months] calendar months, oldest first.
     *
     * A month is scored by how many of its days had an entry, out of the days
     * it has had so far: the current month is not marked down for the days
     * that have not happened yet (§5.2, non-logging never lowers a score).
     */
    fun recordedDaysPerMonth(today: Int, months: Int = 6): List<MonthCoverage> {
        val days = allRecordedDays()
        var month = YearMonth.of(today)
        val out = ArrayDeque<MonthCoverage>()
        repeat(months) {
            val last = minOf(month.lastDay, today)
            val elapsed = (last - month.firstDay + 1).coerceAtLeast(0)
            val recorded = (month.firstDay..last).count { it in days }
            out.addFirst(MonthCoverage(month, recorded, elapsed))
            month = month.previous()
        }
        return out.toList()
    }

    /**
     * The longest run of consecutive recorded days, ever.
     *
     * §5.4: on a break, keep "longest" prominent. It is the one figure a lapse
     * cannot take away, which is why it is shown rather than a current streak
     * that would reset to zero and make the return costlier than the lapse.
     */
    fun longestRun(): Int {
        val days = allRecordedDays().sorted()
        var best = 0
        var run = 0
        var previous: Int? = null
        for (d in days) {
            run = if (previous != null && d == previous + 1) run + 1 else 1
            if (run > best) best = run
            previous = d
        }
        return best
    }
}

/** How much of one month was recorded on: [recorded] of [elapsed] days. */
data class MonthCoverage(val month: YearMonth, val recorded: Int, val elapsed: Int)

/** A day the user said had nothing in it. See [LedgerRepository.markNothingSpent]. */
object NoSpend {
    const val ENTITY = "nospend"
    const val FIELD_MARKED = "marked"
}
