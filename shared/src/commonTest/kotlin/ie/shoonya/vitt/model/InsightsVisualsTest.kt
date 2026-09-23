package ie.shoonya.vitt.model

import ie.shoonya.vitt.capture.Category
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.time.Civil
import ie.shoonya.vitt.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The figures behind the donut, the calendar heatmap and the size histogram. */
class InsightsVisualsTest {

    private val sept = YearMonth(2026, 9)
    private fun day(d: Int) = Civil.toDays(2026, 9, d)

    private fun spend(id: String, minor: Long, d: Int = 3, currency: Currency = Currency.EUR, category: String? = "groceries") =
        Transaction(
            id = id, amount = Money(-minor, currency), merchant = null, category = category, categorySource = null,
            accountId = null, day = day(d), totalPaid = null, splitWith = emptySet(),
            settled = Money(0, currency), note = null, deleted = false,
        )

    @Test
    fun `each day of the month so far has its total — zero on a clear day`() {
        val days = Insights.dailyTotals(
            listOf(spend("a", 500, d = 1), spend("b", 250, d = 1), spend("c", 1000, d = 3)),
            Currency.EUR, sept, today = day(4),
        )
        assertEquals(listOf(750L, 0L, 1000L, 0L), days.map { it.minor })
    }

    @Test
    fun `the heatmap never shows another currency's spending`() {
        val days = Insights.dailyTotals(listOf(spend("a", 500, currency = Currency.INR)), Currency.EUR, sept, today = day(3))
        assertTrue(days.all { it.minor == 0L })
    }

    @Test
    fun `past six slices the tail folds into one — and the total is unchanged`() {
        val categories = listOf("groceries", "dining", "transport", "shopping", "bills", "health", "travel", "entertainment")
        val slices = Insights.byCategory(
            categories.mapIndexed { i, c -> spend("t$i", (10 - i) * 100L, category = c) },
            Currency.EUR,
        )
        val folded = Insights.foldTail(slices)
        assertEquals(6, folded.size)
        assertNull(folded.last().category)
        assertEquals(slices.sumOf { it.spent.minor }, folded.sumOf { it.spent.minor })
        assertEquals(Category.ofCode("groceries"), folded.first().category)
    }

    @Test
    fun `a few slices are left as they are`() {
        val slices = Insights.byCategory(listOf(spend("a", 100), spend("b", 200, category = "dining")), Currency.EUR)
        assertEquals(slices, Insights.foldTail(slices))
    }

    @Test
    fun `purchase sizes fall between round amounts and every purchase is counted once`() {
        val amounts = listOf(150L, 350, 420, 800, 1200, 1500, 1800, 2500, 4000, 9000)
        val bins = Insights.sizes(amounts.mapIndexed { i, m -> spend("t$i", m) }, Currency.EUR, null)
        assertEquals(amounts.size, bins.sumOf { it.count })
        assertEquals(amounts.sum(), bins.sumOf { it.spent.minor })
        // Round edges in whole euro: 2, 5, 10, 20 and so on, never 11.37.
        bins.mapNotNull { it.until }.forEach { edge ->
            val major = edge.minor / 100
            assertTrue(edge.minor % 100 == 0L && major.toString().trimEnd('0') in setOf("1", "2", "5"), "not round: $edge")
        }
        assertEquals(0L, bins.first().from.minor)
        assertNull(bins.last().until)
        assertTrue(bins.size <= 6)
    }

    @Test
    fun `yen gets edges in yen`() {
        val bins = Insights.sizes(
            listOf(300L, 800, 1200, 2500, 6000).mapIndexed { i, m -> spend("y$i", m, currency = Currency.JPY) },
            Currency.JPY, null,
        )
        assertTrue(bins.mapNotNull { it.until }.all { it.minor >= 1 && it.currency == Currency.JPY })
        assertEquals(5, bins.sumOf { it.count })
    }

    @Test
    fun `nothing spent is no histogram`() {
        assertTrue(Insights.sizes(emptyList(), Currency.EUR, null).isEmpty())
    }
}
