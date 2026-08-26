package ie.shoonya.vitt.model

import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.sync.EventStore

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
        val order = all.asReversed().map { it.amount.currency }.distinct()

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
