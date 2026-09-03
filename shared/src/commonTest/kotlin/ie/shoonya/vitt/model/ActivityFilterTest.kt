package ie.shoonya.vitt.model

import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.sync.EventStore
import ie.shoonya.vitt.sync.testDriver
import ie.shoonya.vitt.time.Civil
import ie.shoonya.vitt.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActivityFilterTest {

    private val node = "a219e7a71cc18912"

    private fun repo(): LedgerRepository {
        var t = 1_000L
        return LedgerRepository(EventStore.open(testDriver(), node) { t++ }) { t }
    }

    private val september = YearMonth(2026, 9)
    private val august = YearMonth(2026, 8)
    private fun sept(d: Int) = Civil.toDays(2026, 9, d)
    private fun aug(d: Int) = Civil.toDays(2026, 8, d)

    private fun LedgerRepository.populate() {
        record("a", Money(-1000, Currency.EUR), day = aug(20), merchant = "TESCO")
        record("b", Money(-2000, Currency.EUR), day = sept(3), merchant = "LIDL")
        record("c", Money(-90_000, Currency.INR), day = sept(4), merchant = "SWIGGY")
        record("d", Money(-500, Currency.EUR), day = sept(4), merchant = "SOMEWHERE ODD")
    }

    private fun ids(days: List<Pair<Int, List<Transaction>>>) =
        days.flatMap { it.second }.map { it.id }.sorted()

    @Test
    fun `unfiltered shows everything — newest day first`() {
        val r = repo()
        r.populate()
        val days = r.byDay()
        assertEquals(listOf("a", "b", "c", "d"), ids(days))
        assertEquals(listOf(sept(4), sept(3), aug(20)), days.map { it.first })
    }

    @Test
    fun `a month narrows to that month only`() {
        val r = repo()
        r.populate()
        assertEquals(listOf("b", "c", "d"), ids(r.byDay(period = september)))
        assertEquals(listOf("a"), ids(r.byDay(period = august)))
    }

    @Test
    fun `a currency narrows without touching the others`() {
        val r = repo()
        r.populate()
        assertEquals(listOf("a", "b", "d"), ids(r.byDay(currency = Currency.EUR)))
        assertEquals(listOf("c"), ids(r.byDay(currency = Currency.INR)))
    }

    @Test
    fun `month and currency compose`() {
        // The axes are independent, which is why they are chips and not tabs.
        val r = repo()
        r.populate()
        assertEquals(listOf("b", "d"), ids(r.byDay(period = september, currency = Currency.EUR)))
        assertEquals(listOf("c"), ids(r.byDay(period = september, currency = Currency.INR)))
        assertTrue(r.byDay(period = august, currency = Currency.INR).isEmpty())
    }

    @Test
    fun `the review queue is what the tiers could not place`() {
        // §6.1 asks for this. "SOMEWHERE ODD" matches no seed and no rule.
        val r = repo()
        r.populate()
        assertEquals(listOf("d"), ids(r.byDay(needingCategory = true)))
        assertEquals(1, r.needingCategoryCount())
    }

    @Test
    fun `categorising something empties the review queue`() {
        val r = repo()
        r.populate()
        r.categorise("d", ie.shoonya.vitt.capture.Category.MISCELLANEOUS)
        assertTrue(r.byDay(needingCategory = true).isEmpty())
        assertEquals(0, r.needingCategoryCount())
    }

    @Test
    fun `the review count can be scoped to a month`() {
        val r = repo()
        r.populate()
        r.record("e", Money(-700, Currency.EUR), day = aug(2), merchant = "ANOTHER ODD ONE")
        assertEquals(2, r.needingCategoryCount())
        assertEquals(1, r.needingCategoryCount(september))
        assertEquals(1, r.needingCategoryCount(august))
    }

    @Test
    fun `months with activity are listed newest first and deduplicated`() {
        val r = repo()
        r.populate()
        assertEquals(listOf(september, august), r.monthsWithActivity())
    }

    @Test
    fun `an empty log has no months`() {
        assertTrue(repo().monthsWithActivity().isEmpty())
    }

    @Test
    fun `a deleted transaction leaves every view`() {
        val r = repo()
        r.populate()
        r.delete("c")
        assertTrue(r.byDay(currency = Currency.INR).isEmpty())
        assertEquals(listOf(september, august), r.monthsWithActivity())
    }

    @Test
    fun `transfers never appear in the activity list`() {
        // They are not spending, and the list is of transactions.
        val r = repo()
        r.openAccount("eur", "AIB", Currency.EUR, opening = Money(100_000, Currency.EUR))
        r.openAccount("inr", "HDFC", Currency.INR)
        r.transfer(
            "m1", "eur", "inr",
            Money(50_000, Currency.EUR), Money(4_500_000, Currency.INR), day = sept(5),
        )
        assertTrue(r.byDay().isEmpty())
        assertTrue(r.monthsWithActivity().isEmpty())
    }
}
