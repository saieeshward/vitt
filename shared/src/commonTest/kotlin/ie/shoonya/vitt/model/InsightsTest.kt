package ie.shoonya.vitt.model

import ie.shoonya.vitt.capture.Category
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.sync.EventStore
import ie.shoonya.vitt.sync.testDriver
import ie.shoonya.vitt.time.Civil
import ie.shoonya.vitt.time.Week
import ie.shoonya.vitt.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InsightsTest {

    private val node = "a219e7a71cc18912"

    private fun repo(): LedgerRepository {
        var t = 1_000L
        return LedgerRepository(EventStore.open(testDriver(), node) { t++ }) { t }
    }

    private val september = YearMonth(2026, 9)
    private val august = YearMonth(2026, 8)
    private fun sept(d: Int) = Civil.toDays(2026, 9, d)
    private fun aug(d: Int) = Civil.toDays(2026, 8, d)

    private fun eur(minor: Long) = Money(minor, Currency.EUR)
    private fun inr(minor: Long) = Money(minor, Currency.INR)

    private fun LedgerRepository.populate() {
        record("a", eur(-1_000), day = sept(2), merchant = "TESCO DUBLIN", category = "groceries")
        record("b", eur(-2_500), day = sept(3), merchant = "TESCO NAAS 4471", category = "groceries")
        record("c", eur(-4_000), day = sept(4), merchant = "AER LINGUS", category = "travel")
        record("d", eur(-500), day = sept(5), merchant = "SOMEWHERE ODD")
        record("e", eur(340_000), day = sept(1), merchant = "PAYROLL", category = "income")
        record("f", inr(-90_000), day = sept(4), merchant = "SWIGGY", category = "dining")
        record("g", eur(-9_000), day = aug(20), merchant = "IKEA", category = "shopping")
    }

    // --- byCategory ---

    @Test
    fun `categories are ranked by spend and never cross a currency`() {
        val r = repo()
        r.populate()
        val slices = Insights.byCategory(r.transactions(), Currency.EUR, september)
        assertEquals(
            listOf(Category.TRAVEL, Category.GROCERIES, null),
            slices.map { it.category },
        )
        assertEquals(eur(4_000), slices[0].spent)
        // Both TESCO rows, summed.
        assertEquals(eur(3_500), slices[1].spent)
        assertEquals(2, slices[1].count)
        // The rupee dining row is nowhere in a EUR breakdown.
        assertTrue(slices.none { it.category == Category.DINING })
    }

    @Test
    fun `income is not a slice of a spending breakdown`() {
        val r = repo()
        r.populate()
        val slices = Insights.byCategory(r.transactions(), Currency.EUR, september)
        assertTrue(slices.none { it.category == Category.INCOME })
    }

    @Test
    fun `uncategorised is its own slice and sorts last whatever it is worth`() {
        val r = repo()
        // Deliberately the largest amount on the screen, to prove the ordering
        // rule is not just an artefact of it being small.
        r.record("x", eur(-99_000), day = sept(6))
        r.record("y", eur(-1_000), day = sept(6), category = "dining")
        val slices = Insights.byCategory(r.transactions(), Currency.EUR, september)
        assertEquals(listOf(Category.DINING, null), slices.map { it.category })
        assertEquals("No category", slices[1].label)
    }

    @Test
    fun `the slices sum to exactly the ledger card figure`() {
        // The reason the uncategorised slice is kept rather than dropped: a chart
        // that disagrees with the number above it reads as a bug in both.
        val r = repo()
        r.populate()
        val slices = Insights.byCategory(r.transactions(), Currency.EUR, september)
        val charted = slices.fold(eur(0)) { acc, s -> acc + s.spent }
        val card = r.ledgers(september).first { it.currency == Currency.EUR }.spent
        assertEquals(card, charted)
    }

    @Test
    fun `a period with nothing in it has no slices`() {
        val r = repo()
        r.populate()
        assertEquals(emptyList(), Insights.byCategory(r.transactions(), Currency.EUR, YearMonth(2025, 1)))
    }

    @Test
    fun `all time is the absence of a period rather than a filter`() {
        val r = repo()
        r.populate()
        val all = Insights.byCategory(r.transactions(), Currency.EUR, period = null)
        // August's IKEA row is in, and September's are too.
        assertEquals(eur(9_000), all.first { it.category == Category.SHOPPING }.spent)
        assertEquals(eur(3_500), all.first { it.category == Category.GROCERIES }.spent)
    }

    // --- trend ---

    @Test
    fun `a trend is oldest first and includes empty periods as zero`() {
        val r = repo()
        r.populate()
        val trend = Insights.trend(r.transactions(), Currency.EUR, september, count = 3)
        assertEquals(listOf(YearMonth(2026, 7), august, september), trend.map { it.period })
        // July is empty and still present — a gap that closed up would be a
        // chart lying about its own axis.
        assertEquals(eur(0), trend[0].spent)
        assertEquals(eur(9_000), trend[1].spent)
        assertEquals(eur(8_000), trend[2].spent)
    }

    @Test
    fun `a trend carries income separately and never nets it against spending`() {
        val r = repo()
        r.populate()
        val trend = Insights.trend(r.transactions(), Currency.EUR, september, count = 1)
        assertEquals(eur(8_000), trend[0].spent)
        assertEquals(eur(340_000), trend[0].received)
    }

    @Test
    fun `a trend works at any grain because stepping back is the period's own job`() {
        val r = repo()
        r.record("m", eur(-1_000), day = sept(3))
        r.record("n", eur(-2_000), day = sept(10))
        val weeks = Insights.trend(
            r.transactions(),
            Currency.EUR,
            Week.containing(sept(10)),
            count = 2,
        )
        assertEquals(eur(1_000), weeks[0].spent)
        assertEquals(eur(2_000), weeks[1].spent)
    }

    @Test
    fun `a trend of no periods is a caller bug`() {
        assertFailsWith<IllegalArgumentException> {
            Insights.trend(emptyList(), Currency.EUR, september, count = 0)
        }
    }

    @Test
    fun `the peak of an empty trend is zero rather than a division waiting to happen`() {
        assertEquals(0L, emptyList<PeriodSlice>().peakSpent())
    }

    // --- topMerchants ---

    @Test
    fun `merchants group by the app's one definition of the same merchant`() {
        val r = repo()
        r.populate()
        val top = Insights.topMerchants(r.transactions(), Currency.EUR, september)
        // TESCO DUBLIN and TESCO NAAS 4471 are one row, exactly as they are one
        // rule — grouping on the raw acquirer string put the same shop on the
        // chart twice, once per terminal.
        assertEquals(1, top.count { it.label.equals("Tesco", ignoreCase = true) })
        assertEquals(eur(3_500), top.first { it.label.equals("Tesco", ignoreCase = true) }.spent)
        assertEquals(2, top.first { it.label.equals("Tesco", ignoreCase = true) }.count)
    }

    @Test
    fun `merchants are ranked by spend and capped at the limit`() {
        val r = repo()
        r.populate()
        val top = Insights.topMerchants(r.transactions(), Currency.EUR, september, limit = 2)
        assertEquals(2, top.size)
        assertTrue(top[0].spent.minor >= top[1].spent.minor)
    }

    @Test
    fun `a row with no merchant is omitted rather than pooled under a dash`() {
        val r = repo()
        r.record("p", eur(-5_000), day = sept(7))
        r.record("q", eur(-1_000), day = sept(7), merchant = "LIDL")
        val top = Insights.topMerchants(r.transactions(), Currency.EUR, september)
        assertEquals(1, top.size)
        assertEquals("Lidl", top[0].label)
    }

    @Test
    fun `transfers between the user's own accounts never appear as spending`() {
        val r = repo()
        r.openAccount("cur", "Current", Currency.EUR, AccountKind.CURRENT, eur(100_000))
        r.openAccount("sav", "Savings", Currency.EUR, AccountKind.SAVINGS, eur(0))
        r.transfer("t1", "cur", "sav", eur(50_000), eur(50_000), day = sept(8))
        r.record("z", eur(-1_000), day = sept(8), category = "dining")
        val slices = Insights.byCategory(r.transactions(), Currency.EUR, september)
        // €500 moved to savings is not €500 of spending. Transfers are not
        // transactions, so this holds by construction rather than by a filter.
        assertEquals(eur(1_000), slices.fold(eur(0)) { acc, s -> acc + s.spent })
    }

    // --- shareOf ---

    @Test
    fun `a share is of its own currency or it is a caller bug`() {
        val slice = CategorySlice(Category.DINING, eur(2_500), 1)
        assertEquals(0.25f, slice.shareOf(eur(10_000)))
        assertFailsWith<IllegalArgumentException> { slice.shareOf(inr(10_000)) }
    }

    @Test
    fun `a share of nothing is zero rather than a divide by zero`() {
        assertEquals(0f, CategorySlice(Category.DINING, eur(0), 0).shareOf(eur(0)))
    }
}
