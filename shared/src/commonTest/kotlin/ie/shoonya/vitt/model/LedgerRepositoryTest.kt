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
        assertEquals("Groceries", t.category)
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

        val budgeted = r.ledgers(mapOf(Currency.EUR to Money(180000, Currency.EUR))).single()
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
}
