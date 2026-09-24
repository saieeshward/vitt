package ie.shoonya.vitt.model

import ie.shoonya.vitt.capture.Category
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.time.Civil
import ie.shoonya.vitt.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MonthReviewTest {

    private val aug = YearMonth(2026, 8)
    private fun eur(m: Long) = Money(m, Currency.EUR)
    private fun day(month: Int, d: Int) = Civil.toDays(2026, month, d)

    private fun t(
        id: String,
        minor: Long,
        d: Int,
        month: Int = 8,
        category: String? = "groceries",
        merchant: String? = null,
        currency: Currency = Currency.EUR,
    ) = Transaction(
        id = id, amount = Money(-minor, currency), merchant = merchant, category = category, categorySource = null,
        accountId = null, day = day(month, d), totalPaid = null, splitWith = emptySet(),
        settled = Money(0, currency), note = null, deleted = false,
    )

    private val august = listOf(
        t("a", 4000, 1, merchant = "TESCO"),
        t("b", 2500, 1, category = "dining"),
        t("c", 1500, 3, merchant = "TESCO"),
        t("d", 12000, 9, category = "transport"),
        t("e", 1000, 9, merchant = "TESCO"),
        t("f", 500, 20, category = "dining"),
    )

    @Test
    fun `a finished month tells its facts`() {
        val r = MonthReview.of(august, Currency.EUR, aug, today = day(9, 2))!!
        assertTrue(r.complete)
        assertEquals(eur(21500), r.spent)
        assertEquals(6, r.entries)
        assertEquals(4, r.daysRecorded)
        assertEquals(31, r.daysCounted)
        assertEquals(27, r.clearDays)
        assertEquals(Category.ofCode("transport"), r.topCategory?.category)
        assertEquals(55, r.topShare)
        assertEquals(day(8, 9) to eur(13000), r.biggestDay)
        assertEquals(3, r.regular?.count)
    }

    @Test
    fun `a month in progress counts only the days so far`() {
        val r = MonthReview.of(august, Currency.EUR, aug, today = day(8, 10))!!
        assertFalse(r.complete)
        assertEquals(10, r.daysCounted)
        assertEquals(7, r.clearDays)
    }

    @Test
    fun `too little recorded is no review rather than a thin one`() {
        assertNull(MonthReview.of(august.take(4), Currency.EUR, aug, today = day(9, 2)))
    }

    @Test
    fun `another currency is never counted in`() {
        val mixed = august.take(4) + t("x", 99999, 5, currency = Currency.INR)
        assertNull(MonthReview.of(mixed, Currency.EUR, aug, today = day(9, 2)))
    }

    @Test
    fun `against last month is a figure — and a small difference reads as about the same`() {
        val july = listOf(t("j", 21000, 12, month = 7))
        val r = MonthReview.of(august + july, Currency.EUR, aug, today = day(9, 2))!!
        assertEquals(eur(500), r.versusLast)
        assertTrue(r.aboutTheSame)

        val quiet = listOf(t("j", 40000, 12, month = 7))
        val s = MonthReview.of(august + quiet, Currency.EUR, aug, today = day(9, 2))!!
        assertEquals(eur(-18500), s.versusLast)
        assertFalse(s.aboutTheSame)
    }

    @Test
    fun `no last month is no comparison`() {
        assertNull(MonthReview.of(august, Currency.EUR, aug, today = day(9, 2))!!.versusLast)
    }

    @Test
    fun `the budget is room left or a figure past it`() {
        val within = MonthReview.of(august, Currency.EUR, aug, today = day(9, 2), budget = eur(30000))!!
        assertEquals(eur(8500), within.room)
        assertNull(within.past)
        val over = MonthReview.of(august, Currency.EUR, aug, today = day(9, 2), budget = eur(20000))!!
        assertEquals(eur(1500), over.past)
        assertNull(over.room)
    }

    @Test
    fun `a merchant visited once is not a regular`() {
        val once = august.map { if (it.merchant != null) it.copy(merchant = "SHOP ${it.id}") else it }
        assertNull(MonthReview.of(once, Currency.EUR, aug, today = day(9, 2))!!.regular)
    }

    @Test
    fun `what has no category is counted beside the top category`() {
        val r = MonthReview.of(august + t("u", 30000, 4, category = null), Currency.EUR, aug, today = day(9, 2))!!
        assertEquals(eur(30000), r.uncategorised)
        assertEquals(Category.ofCode("transport"), r.topCategory?.category)
    }

    @Test
    fun `days before logging began are not counted as days with nothing out`() {
        // Started on the 9th: the 1st to the 8th were before the app.
        val late = august.filter { it.day >= day(8, 9) } + listOf(
            t("g", 700, 10), t("h", 300, 12), t("i", 900, 15),
        )
        val r = MonthReview.of(late, Currency.EUR, aug, today = day(9, 2), trackedFrom = day(8, 9))!!
        assertEquals(day(8, 9), r.countedFrom)
        assertEquals(23, r.daysCounted)
        assertEquals(18, r.clearDays)
    }

    @Test
    fun `days left counts to the end of the month and is zero once it is over`() {
        // The 20th of a 31-day month: eleven days still ahead.
        assertEquals(11, MonthReview.of(august, Currency.EUR, aug, today = day(8, 20))!!.daysLeft)
        assertEquals(0, MonthReview.of(august, Currency.EUR, aug, today = day(9, 2))!!.daysLeft)
    }
}
