package ie.shoonya.vitt.model

import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.sync.EventStore
import ie.shoonya.vitt.sync.testDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LedgerRepositoryTest {

    private val node = "a219e7a71cc18912"

    private fun repo(): LedgerRepository {
        var t = 1_000L
        return LedgerRepository(EventStore.open(testDriver(), node) { t++ }) { t }
    }

    @Test
    fun `a recorded transaction reads back`() {
        val r = repo()
        r.record("t1", Money(-1250, Currency.EUR), day = 20_000, merchant = "Tesco", category = "Groceries")
        val t = r.transactions().single()
        assertEquals(-1250L, t.amount.minor)
        assertEquals("Tesco", t.merchant)
        assertEquals("groceries", t.category)
    }

    @Test
    fun `ledgers stay separate and are never summed`() {
        val r = repo()
        r.record("t1", Money(-1250, Currency.EUR), day = 20_000)
        r.record("t2", Money(-50000, Currency.INR), day = 20_000)

        val ledgers = r.ledgers()
        assertEquals(2, ledgers.size)
        assertEquals(setOf(Currency.EUR, Currency.INR), ledgers.map { it.currency }.toSet())
        // There is no combined total to assert on — the type has no field for it.
        assertEquals(1250L, ledgers.first { it.currency == Currency.EUR }.spent.minor)
        assertEquals(50000L, ledgers.first { it.currency == Currency.INR }.spent.minor)
    }

    @Test
    fun `currency colour order does not shift when spending changes`() {
        // Order is by first appearance, so a currency's assigned colour cannot
        // change under the user because they spent more in another one.
        val r = repo()
        r.record("t1", Money(-100, Currency.EUR), day = 20_000)
        r.record("t2", Money(-100, Currency.INR), day = 20_001)
        val before = r.ledgers().map { it.currency to it.index }

        r.record("t3", Money(-9_999_999, Currency.INR), day = 20_002)
        assertEquals(before, r.ledgers().map { it.currency to it.index })
    }

    @Test
    fun `income and spending are tracked separately`() {
        val r = repo()
        r.record("t1", Money(-1250, Currency.EUR), day = 20_000)
        r.record("t2", Money(341000, Currency.EUR), day = 20_000)
        val eur = r.ledgers().single()
        assertEquals(1250L, eur.spent.minor)
        assertEquals(341000L, eur.received.minor)
        assertEquals(339750L, eur.net.minor)
    }

    @Test
    fun `room left is shown — and is absent rather than negative when unset`() {
        val r = repo()
        r.record("t1", Money(-1250, Currency.EUR), day = 20_000)
        assertNull(r.ledgers().single().remaining(), "no budget means no figure, not zero")

        r.setBudget(Currency.EUR, Money(180000, Currency.EUR))
        // A month, because a monthly limit is only meaningful at month grain —
        // ledgers() withholds it for a week, a year or all time rather than
        // pro-rating it into a number nobody agreed to.
        val budgeted = r.ledgers(ie.shoonya.vitt.time.YearMonth.of(20_000)).single()
        assertEquals(178750L, budgeted.remaining()?.minor)
        assertEquals(1250f / 180000f, budgeted.pressure())
    }

    @Test
    fun `a split records your share as the amount and what was paid beside it`() {
        val r = repo()
        r.record(
            "t1",
            amount = Money(-2305, Currency.EUR),
            day = 20_000,
            totalPaid = Money(-4610, Currency.EUR),
        )
        val t = r.transactions().single()
        assertTrue(t.isSplit)
        assertEquals(-2305L, t.share.minor, "the budget sees your share")
        assertEquals(2305L, t.owed()?.minor)
    }

    @Test
    fun `a split cannot cross currencies`() {
        val r = repo()
        assertFailsWith<IllegalArgumentException> {
            r.record(
                "t1",
                amount = Money(-2305, Currency.EUR),
                day = 20_000,
                totalPaid = Money(-4610, Currency.INR),
            )
        }
    }

    @Test
    fun `owed amounts are per currency and never netted together`() {
        val r = repo()
        r.record("t1", Money(-2305, Currency.EUR), day = 20_000, totalPaid = Money(-4610, Currency.EUR))
        r.record("t2", Money(-60000, Currency.INR), day = 20_000, totalPaid = Money(-120000, Currency.INR))

        val owed = r.owed()
        assertEquals(2, owed.size)
        assertEquals(2305L, owed[Currency.EUR]?.minor)
        assertEquals(60000L, owed[Currency.INR]?.minor)
    }

    @Test
    fun `a deleted transaction disappears from the list`() {
        val r = repo()
        r.record("t1", Money(-1250, Currency.EUR), day = 20_000)
        r.delete("t1")
        assertEquals(0, r.transactions().size)
    }

    @Test
    fun `the activity list groups by day — newest first`() {
        val r = repo()
        r.record("t1", Money(-100, Currency.EUR), day = 20_000)
        r.record("t2", Money(-200, Currency.EUR), day = 20_002)
        r.record("t3", Money(-300, Currency.EUR), day = 20_002)

        val days = r.byDay()
        assertEquals(listOf(20_002, 20_000), days.map { it.first })
        assertEquals(2, days.first().second.size)
    }

    @Test
    fun `the streak counts days recorded — not days under budget`() {
        // Rewarding thrift punishes the month someone flew home for a funeral.
        val r = repo()
        r.record("t1", Money(-999_999, Currency.EUR), day = 20_000)
        r.record("t2", Money(-1, Currency.EUR), day = 20_001)
        r.record("t3", Money(-1, Currency.EUR), day = 20_001)

        assertEquals(2, r.daysRecorded(today = 20_001), "two days recorded, regardless of amount")
    }

    @Test
    fun `an unreadable entity is skipped rather than crashing the list`() {
        val store = EventStore.open(testDriver(), node) { 1_000L }
        // An entity with an amount but no currency — a hand-edited row.
        store.append(
            ie.shoonya.vitt.sync.Event(
                store.issue(), Transaction.ENTITY, "broken",
                Transaction.FIELD_AMOUNT, ie.shoonya.vitt.sync.TaggedValue.Num(-100),
            ),
            1L,
        )
        val r = LedgerRepository(store) { 1_000L }
        assertEquals(0, r.transactions().size)
    }

    @Test
    fun `a note set after recording reads back`() {
        val r = repo()
        r.record("t1", Money(-1_250, Currency.EUR), day = 20_000, merchant = "Tesco")
        assertNull(r.transactions().single().note)

        r.setNote("t1", "  Birthday cake  ")
        assertEquals("Birthday cake", r.transactions().single().note)
    }

    @Test
    fun `a blank note clears the old one`() {
        val r = repo()
        r.record("t1", Money(-1_250, Currency.EUR), day = 20_000, note = "Deposit back")
        assertEquals("Deposit back", r.transactions().single().note)

        r.setNote("t1", "   ")
        assertNull(r.transactions().single().note)

        r.setNote("t1", "Again")
        r.setNote("t1", null)
        assertNull(r.transactions().single().note)
    }

    @Test
    fun `a note survives categorising the entry`() {
        // The category and the note are separate fields, so one write must
        // never disturb the other: a correction should not cost the reminder.
        val r = repo()
        r.record("t1", Money(-1_250, Currency.EUR), day = 20_000, merchant = "Tesco", note = "Party food")
        r.categorise("t1", ie.shoonya.vitt.capture.Category.DINING, applyToPast = true)
        val t = r.transactions().single()
        assertEquals("Party food", t.note)
        assertEquals("dining", t.category)
    }
}

class CurrencyOrderTest {

    private val node = "a219e7a71cc18912"

    private fun repo(): LedgerRepository {
        var t = 1_000L
        return LedgerRepository(EventStore.open(testDriver(), node) { t++ }) { t }
    }

    @Test
    fun `colour order follows the oldest transaction — not how ids sort`() {
        // The bug this covers: the order came from reversing a list sorted by id
        // descending, so a currency's assigned colour depended on id ordering
        // rather than on which currency the user actually used first.
        val r = repo()
        // EUR is used first, on the earlier day, but sorts later by id.
        r.record("zzz-eur", Money(-100, Currency.EUR), day = 20_000)
        r.record("aaa-inr", Money(-100, Currency.INR), day = 20_005)

        val ledgers = r.ledgers()
        assertEquals(Currency.EUR, ledgers.first { it.index == 0 }.currency)
        assertEquals(Currency.INR, ledgers.first { it.index == 1 }.currency)
    }

    @Test
    fun `a later currency takes the next index — it does not displace the first`() {
        val r = repo()
        r.record("a", Money(-100, Currency.EUR), day = 20_000)
        val before = r.ledgers().single { it.currency == Currency.EUR }.index

        r.record("b", Money(-100, Currency.INR), day = 20_001)
        r.record("c", Money(-100, Currency.GBP), day = 20_002)

        assertEquals(before, r.ledgers().single { it.currency == Currency.EUR }.index)
        assertEquals(1, r.ledgers().single { it.currency == Currency.INR }.index)
        assertEquals(2, r.ledgers().single { it.currency == Currency.GBP }.index)
    }

    @Test
    fun `a row labelled transfer never counts as income or spend`() {
        val r = repo()
        r.record("t1", Money(200_000, Currency.EUR), day = 20_000, category = "transfer")
        r.record("t2", Money(-5_000, Currency.EUR), day = 20_000, category = "transfer")
        r.record("t3", Money(-3_000, Currency.EUR), day = 20_000, category = "dining")
        val eur = r.ledgers().single { it.currency == Currency.EUR }
        assertEquals(0L, eur.received.minor, "moving your own money is not income")
        assertEquals(3_000L, eur.spent.minor, "and not spending either")
    }

    @Test
    fun `a blank merchant reads back as none so the note can title the row`() {
        val r = repo()
        r.record("n1", Money(-2_550, Currency.EUR), day = 20_000, merchant = "   ", note = "Team lunch")
        val t = r.transactions().single()
        assertNull(t.merchant)
        assertNull(t.merchantLabel)
        assertEquals("Team lunch", t.note)
    }

    @Test
    fun `a zero amount is refused before it reaches the log`() {
        val r = repo()
        assertFailsWith<IllegalArgumentException> {
            r.record("z1", Money(0, Currency.EUR), day = 20_000)
        }
        assertTrue(r.transactions().isEmpty())
    }

    @Test
    fun `the add screen never offers transfer as a category`() {
        val r = repo()
        assertTrue(r.frequentCategories(20_000, spending = false).none { it == ie.shoonya.vitt.capture.Category.TRANSFER })
        assertTrue(r.frequentCategories(20_000, spending = true).none { it == ie.shoonya.vitt.capture.Category.TRANSFER })
    }

    @Test
    fun `a row with no day or a zero amount is not a transaction`() {
        var t = 1_000L
        val store = EventStore.open(testDriver(), node) { t++ }
        val r = LedgerRepository(store) { t }
        fun put(id: String, field: String, value: ie.shoonya.vitt.sync.TaggedValue) =
            store.append(ie.shoonya.vitt.sync.Event(store.issue(), Transaction.ENTITY, id, field, value), nowMillis = t)
        // Hand-edited row: amount and currency but the day cell was wiped.
        put("noday", Transaction.FIELD_AMOUNT, ie.shoonya.vitt.sync.TaggedValue.Num(-500))
        put("noday", Transaction.FIELD_CURRENCY, ie.shoonya.vitt.sync.TaggedValue.Str("EUR"))
        // Zero row written by an older build.
        put("zero", Transaction.FIELD_AMOUNT, ie.shoonya.vitt.sync.TaggedValue.Num(0))
        put("zero", Transaction.FIELD_CURRENCY, ie.shoonya.vitt.sync.TaggedValue.Str("EUR"))
        put("zero", Transaction.FIELD_DAY, ie.shoonya.vitt.sync.TaggedValue.Num(20_000))
        assertTrue(r.transactions().isEmpty(), "neither can be shown as a transaction")
        assertEquals(0, r.recordedDays(20_000).size, "and neither counts as a recorded day")
    }
}
