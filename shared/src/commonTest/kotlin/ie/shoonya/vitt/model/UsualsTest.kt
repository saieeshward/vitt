package ie.shoonya.vitt.model

import ie.shoonya.vitt.capture.Category
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsualsTest {

    private val today = 20_000

    private fun t(
        id: String,
        minor: Long,
        daysAgo: Int,
        note: String? = null,
        merchant: String? = null,
        category: String? = "dining",
        account: String? = "aib",
        totalPaid: Money? = null,
    ) = Transaction(
        id = id, amount = Money(minor, Currency.EUR), merchant = merchant, category = category, categorySource = null,
        accountId = account, day = today - daysAgo, totalPaid = totalPaid, splitWith = emptySet(),
        settled = Money(0, Currency.EUR), note = note, deleted = false,
    )

    @Test
    fun `the same thing at the same price twice is a usual`() {
        val usuals = Usuals.of(listOf(t("a", -450, 1, note = "Coffee"), t("b", -450, 3, note = "coffee")), today)
        assertEquals(1, usuals.size)
        assertEquals("Coffee", usuals.single().label)
        assertEquals(Money(-450, Currency.EUR), usuals.single().amount)
        assertEquals(2, usuals.single().count)
    }

    @Test
    fun `a different price is a different purchase`() {
        assertTrue(Usuals.of(listOf(t("a", -4100, 1, note = "Tesco"), t("b", -1200, 3, note = "Tesco")), today).isEmpty())
    }

    @Test
    fun `no label is never offered — a bare figure is not recognisable`() {
        assertTrue(Usuals.of(listOf(t("a", -450, 1), t("b", -450, 2)), today).isEmpty())
    }

    @Test
    fun `the merchant stands in for a missing note`() {
        val usuals = Usuals.of(listOf(t("a", -299, 1, merchant = "NETFLIX.COM"), t("b", -299, 31, merchant = "NETFLIX.COM")), today)
        assertEquals(1, usuals.size)
    }

    @Test
    fun `the latest entry decides the category and account`() {
        val usuals = Usuals.of(
            listOf(
                t("old", -800, 9, note = "Lunch", category = "groceries", account = "aib"),
                t("new", -800, 2, note = "Lunch", category = "dining", account = "revolut"),
            ),
            today,
        )
        assertEquals(Category.ofCode("dining"), usuals.single().category)
        assertEquals("revolut", usuals.single().accountId)
    }

    @Test
    fun `most often first — and old habits fall out of the window`() {
        val rows = listOf(
            t("c1", -450, 1, note = "Coffee"), t("c2", -450, 2, note = "Coffee"), t("c3", -450, 3, note = "Coffee"),
            t("b1", -210, 1, note = "Bus"), t("b2", -210, 4, note = "Bus"),
            t("g1", -999, Usuals.WINDOW_DAYS + 1, note = "Gym"), t("g2", -999, Usuals.WINDOW_DAYS + 5, note = "Gym"),
        )
        assertEquals(listOf("Coffee", "Bus"), Usuals.of(rows, today).map { it.label })
    }

    @Test
    fun `splits and transfers are left out`() {
        val rows = listOf(
            t("s1", -1500, 1, note = "Dinner", totalPaid = Money(-3000, Currency.EUR)),
            t("s2", -1500, 2, note = "Dinner", totalPaid = Money(-3000, Currency.EUR)),
            t("t1", -10000, 1, note = "Savings", category = "transfer"),
            t("t2", -10000, 8, note = "Savings", category = "transfer"),
        )
        assertTrue(Usuals.of(rows, today).isEmpty())
    }

    @Test
    fun `income repeats too`() {
        val usuals = Usuals.of(listOf(t("p1", 250000, 1, note = "Salary", category = null), t("p2", 250000, 31, note = "Salary", category = null)), today)
        assertTrue(usuals.single().amount.isInflow)
    }
}
