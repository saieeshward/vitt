package ie.shoonya.vitt.model

import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.sync.Event
import ie.shoonya.vitt.sync.EventStore
import ie.shoonya.vitt.sync.TaggedValue
import ie.shoonya.vitt.sync.testDriver
import ie.shoonya.vitt.time.Civil
import ie.shoonya.vitt.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BudgetTest {

    private val node = "a219e7a71cc18912"

    private class Harness(val store: EventStore, val now: () -> Long) {
        val repo = LedgerRepository(store, now)

        fun put(entity: String, id: String, field: String, value: TaggedValue) {
            store.append(Event(store.issue(), entity, id, field, value), now())
        }
    }

    private fun harness(): Harness {
        var t = 1_000L
        val store = EventStore.open(testDriver(), node) { t++ }
        return Harness(store) { t }
    }

    private fun repo(): LedgerRepository = harness().repo

    private val september = YearMonth(2026, 9)
    private fun sept(day: Int) = Civil.toDays(2026, 9, day)

    @Test
    fun `a budget reads back`() {
        val r = repo()
        r.setBudget(Currency.EUR, Money(180_000, Currency.EUR))
        assertEquals(Money(180_000, Currency.EUR), r.budgets()[Currency.EUR]?.limit)
    }

    @Test
    fun `setting a budget twice edits rather than duplicates`() {
        // The currency code is the entity id, so this has to be an edit however
        // many devices set it.
        val r = repo()
        r.setBudget(Currency.EUR, Money(180_000, Currency.EUR))
        r.setBudget(Currency.EUR, Money(200_000, Currency.EUR))
        assertEquals(1, r.budgets().size)
        assertEquals(Money(200_000, Currency.EUR), r.budgets()[Currency.EUR]?.limit)
    }

    @Test
    fun `budgets are per currency and never one across them`() {
        val r = repo()
        r.setBudget(Currency.EUR, Money(180_000, Currency.EUR))
        r.setBudget(Currency.INR, Money(4_000_000, Currency.INR))
        assertEquals(2, r.budgets().size)
        assertEquals(Currency.EUR, r.budgets()[Currency.EUR]?.limit?.currency)
        assertEquals(Currency.INR, r.budgets()[Currency.INR]?.limit?.currency)
    }

    @Test
    fun `a budget in the wrong currency is refused`() {
        val r = repo()
        assertFailsWith<IllegalArgumentException> {
            r.setBudget(Currency.EUR, Money(180_000, Currency.INR))
        }
    }

    @Test
    fun `a non-positive budget is refused`() {
        val r = repo()
        assertFailsWith<IllegalArgumentException> {
            r.setBudget(Currency.EUR, Money(0, Currency.EUR))
        }
        assertFailsWith<IllegalArgumentException> {
            r.setBudget(Currency.EUR, Money(-100, Currency.EUR))
        }
    }

    @Test
    fun `clearing a budget removes the figure rather than zeroing it`() {
        val r = repo()
        r.record("t1", Money(-1250, Currency.EUR), day = sept(3))
        r.setBudget(Currency.EUR, Money(180_000, Currency.EUR))
        r.clearBudget(Currency.EUR)

        assertTrue(r.budgets().isEmpty())
        val ledger = r.ledgers(september).single()
        assertNull(ledger.budget)
        assertNull(ledger.remaining(), "no budget means no figure, not zero")
        assertNull(ledger.pressure())
    }

    @Test
    fun `a hand-edited zero budget is ignored`() {
        // A budget of nothing makes every expense infinitely over, which is not
        // a state the app should be able to render.
        val h = harness()
        h.put(Budget.ENTITY, "EUR", Budget.FIELD_CURRENCY, TaggedValue.Str("EUR"))
        h.put(Budget.ENTITY, "EUR", Budget.FIELD_LIMIT, TaggedValue.Num(0))
        assertTrue(h.repo.budgets().isEmpty())
    }

    @Test
    fun `a budget row with no limit is ignored`() {
        val h = harness()
        h.put(Budget.ENTITY, "EUR", Budget.FIELD_CURRENCY, TaggedValue.Str("EUR"))
        assertTrue(h.repo.budgets().isEmpty())
    }

    @Test
    fun `a budget row naming no known currency is ignored`() {
        val h = harness()
        h.put(Budget.ENTITY, "XYZ", Budget.FIELD_LIMIT, TaggedValue.Num(1000))
        assertTrue(h.repo.budgets().isEmpty())
    }

    @Test
    fun `room left and pressure come from the month's spending`() {
        val r = repo()
        r.setBudget(Currency.EUR, Money(100_000, Currency.EUR))
        r.record("t1", Money(-25_000, Currency.EUR), day = sept(3))
        r.record("t2", Money(-15_000, Currency.EUR), day = sept(20))

        val ledger = r.ledgers(september).single()
        assertEquals(Money(40_000, Currency.EUR), ledger.spent)
        assertEquals(Money(60_000, Currency.EUR), ledger.remaining())
        assertEquals(0.4f, ledger.pressure())
    }

    @Test
    fun `last month's spending is not in this month's budget`() {
        // The card says "out this month" and has to mean it: ledgers() summed
        // every transaction ever, so a budget could never be met after the first
        // month.
        val r = repo()
        r.setBudget(Currency.EUR, Money(100_000, Currency.EUR))
        r.record("old", Money(-90_000, Currency.EUR), day = Civil.toDays(2026, 8, 20))
        r.record("new", Money(-10_000, Currency.EUR), day = sept(3))

        val ledger = r.ledgers(september).single()
        assertEquals(Money(10_000, Currency.EUR), ledger.spent)
        assertEquals(Money(90_000, Currency.EUR), ledger.remaining())
    }

    @Test
    fun `next month's spending is not in this month either`() {
        val r = repo()
        r.record("future", Money(-5_000, Currency.EUR), day = Civil.toDays(2026, 10, 1))
        r.record("now", Money(-1_000, Currency.EUR), day = sept(30))
        assertEquals(Money(1_000, Currency.EUR), r.ledgers(september).single().spent)
    }

    @Test
    fun `the first and last day of the month are both counted`() {
        val r = repo()
        r.record("first", Money(-1_000, Currency.EUR), day = sept(1))
        r.record("last", Money(-2_000, Currency.EUR), day = sept(30))
        assertEquals(Money(3_000, Currency.EUR), r.ledgers(september).single().spent)
    }

    @Test
    fun `a budget only shows at month grain`() {
        // A monthly limit spread over a week is invented, and wrong in a
        // predictable direction: rent lands on the 1st, so week one always looks
        // catastrophic. The figure is withheld rather than pro-rated.
        val r = repo()
        r.setBudget(Currency.EUR, Money(100_000, Currency.EUR))
        r.record("t1", Money(-10_000, Currency.EUR), day = sept(3))

        assertEquals(
            Money(100_000, Currency.EUR),
            r.ledgers(september).single().budget,
        )
        assertNull(r.ledgers(ie.shoonya.vitt.time.Week.containing(sept(3))).single().budget)
        assertNull(r.ledgers(ie.shoonya.vitt.time.Day(sept(3))).single().budget)
        assertNull(r.ledgers(ie.shoonya.vitt.time.Year(2026)).single().budget)
        assertNull(r.ledgers().single().budget, "all time has no monthly budget either")
    }

    @Test
    fun `spending is still totalled at every grain`() {
        // Withholding the budget must not withhold the figure the period is for.
        val r = repo()
        r.record("t1", Money(-10_000, Currency.EUR), day = sept(3))
        r.record("t2", Money(-2_000, Currency.EUR), day = sept(20))
        assertEquals(
            Money(10_000, Currency.EUR),
            r.ledgers(ie.shoonya.vitt.time.Week.containing(sept(3))).single().spent,
        )
        assertEquals(Money(12_000, Currency.EUR), r.ledgers(september).single().spent)
    }

    @Test
    fun `no month given totals all of history`() {
        val r = repo()
        r.record("old", Money(-90_000, Currency.EUR), day = Civil.toDays(2026, 8, 20))
        r.record("new", Money(-10_000, Currency.EUR), day = sept(3))
        assertEquals(Money(100_000, Currency.EUR), r.ledgers().single().spent)
    }

    @Test
    fun `a currency keeps its colour even in a month it was not used`() {
        // Order is taken from all of history precisely so a card does not change
        // colour because the user happened not to spend in that currency lately.
        val r = repo()
        r.record("eur", Money(-1_000, Currency.EUR), day = Civil.toDays(2026, 7, 1))
        r.record("inr", Money(-90_000, Currency.INR), day = Civil.toDays(2026, 7, 2))
        r.record("inr2", Money(-50_000, Currency.INR), day = sept(3))

        val ledgers = r.ledgers(september)
        assertEquals(0, ledgers.first { it.currency == Currency.EUR }.index)
        assertEquals(1, ledgers.first { it.currency == Currency.INR }.index)
        // EUR is still listed, at zero for the month, rather than vanishing.
        assertEquals(0L, ledgers.first { it.currency == Currency.EUR }.spent.minor)
    }

    @Test
    fun `over budget reports past the limit rather than a negative remainder`() {
        val r = repo()
        r.setBudget(Currency.EUR, Money(10_000, Currency.EUR))
        r.record("t1", Money(-12_000, Currency.EUR), day = sept(3))

        val ledger = r.ledgers(september).single()
        // Remaining goes negative in the model; the UI shows its magnitude with
        // "past" wording, so the hero figure is never a minus sign.
        assertEquals(-2_000L, ledger.remaining()?.minor)
        assertEquals(1.2f, ledger.pressure())
    }

    @Test
    fun `a transfer does not eat into a budget`() {
        // Moving your own money is not spending it.
        val r = repo()
        r.openAccount("eur", "AIB", Currency.EUR, opening = Money(500_000, Currency.EUR))
        r.openAccount("sav", "Savings", Currency.EUR, AccountKind.SAVINGS)
        r.setBudget(Currency.EUR, Money(100_000, Currency.EUR))
        r.transfer(
            "m1", "eur", "sav",
            Money(50_000, Currency.EUR), Money(50_000, Currency.EUR), day = sept(3),
        )
        r.record("t1", Money(-1_000, Currency.EUR), day = sept(4), accountId = "eur")

        val ledger = r.ledgers(september).single()
        assertEquals(Money(1_000, Currency.EUR), ledger.spent)
        assertEquals(Money(99_000, Currency.EUR), ledger.remaining())
    }

    @Test
    fun `a split charges the budget your share — not what you fronted`() {
        val r = repo()
        r.setBudget(Currency.EUR, Money(100_000, Currency.EUR))
        r.record(
            "t1", Money(-2_000, Currency.EUR), day = sept(3),
            totalPaid = Money(-6_000, Currency.EUR), splitWith = setOf("bob@example.com"),
        )
        assertEquals(Money(2_000, Currency.EUR), r.ledgers(september).single().spent)
    }
}
