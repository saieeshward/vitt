package ie.shoonya.vitt.model

import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.money.Rate
import ie.shoonya.vitt.sync.Event
import ie.shoonya.vitt.sync.EventLog
import ie.shoonya.vitt.sync.EventStore
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
    ) {
        require(totalPaid == null || totalPaid.currency == amount.currency) {
            "a split cannot cross currencies"
        }
        // An account this device has not seen yet is not an error: its event may
        // still be in flight from another device, and refusing would lose the
        // transaction outright. Only a *known* mismatch is a caller bug.
        if (accountId != null) {
            val account = accounts(includeArchived = true).firstOrNull { it.id == accountId }
            require(account == null || account.currency == amount.currency) {
                "a transaction cannot be recorded into an account of another currency"
            }
        }
        val events = Transaction.events(
            id = id,
            amount = amount,
            day = day,
            merchant = merchant,
            category = category,
            accountId = accountId,
            totalPaid = totalPaid,
            issue = store::issue,
        )
        val at = now()
        events.forEach { store.append(it, at) }
    }

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
    fun accounts(includeArchived: Boolean = false): List<Account> = store.fold()
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

    /** Every live transaction, newest first. */
    fun transactions(): List<Transaction> = store.fold()
        .mapNotNull { (key, entity) -> Transaction.from(key, entity) }
        .filterNot { it.deleted }
        .sortedWith(compareByDescending<Transaction> { it.day }.thenByDescending { it.id })

    /**
     * One ledger per currency the user actually has, in a stable order.
     *
     * Order is by first appearance rather than by size, so a currency's colour
     * does not change under the user when they spend more in another one.
     */
    fun ledgers(budgets: Map<Currency, Money> = emptyMap()): List<Ledger> {
        val all = transactions()
        // True first appearance: oldest day first, and within a day the order
        // the rows were written. Reversing the display list instead made the
        // order depend on how ids happened to sort, so a currency's colour was
        // effectively arbitrary.
        val order = all
            .sortedWith(compareBy<Transaction> { it.day }.thenBy { it.id })
            .map { it.amount.currency }
            .distinct()

        return order.mapIndexed { index, currency ->
            val forCurrency = all.filter { it.amount.currency == currency }
            Ledger(
                currency = currency,
                index = index,
                spent = forCurrency.filter { it.amount.isOutflow }
                    .fold(Money(0, currency)) { acc, t -> acc + t.amount.abs() },
                received = forCurrency.filter { it.amount.isInflow }
                    .fold(Money(0, currency)) { acc, t -> acc + t.amount },
                budget = budgets[currency],
            )
        }
    }

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
    fun transfers(): List<Transfer> = store.fold()
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
    fun byDay(): List<Pair<Int, List<Transaction>>> =
        transactions().groupBy { it.day }.toList().sortedByDescending { it.first }

    /** Unsettled amounts owed to the user, per currency — never netted across them. */
    fun owed(): Map<Currency, Money> = transactions()
        .mapNotNull { t -> t.owed()?.let { t.amount.currency to it } }
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
    fun daysRecorded(today: Int, window: Int = 30): Int =
        transactions().map { it.day }
            .filter { it > today - window && it <= today }
            .distinct()
            .size
}
