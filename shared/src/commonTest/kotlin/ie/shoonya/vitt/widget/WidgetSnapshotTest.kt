package ie.shoonya.vitt.widget

import ie.shoonya.vitt.model.Ledger
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetSnapshotTest {

    private fun ledger(c: Currency, spent: Long, budget: Long? = null, index: Int = 0) = Ledger(
        currency = c,
        index = index,
        spent = Money(spent, c),
        received = Money(0, c),
        budget = budget?.let { Money(it, c) },
    )

    @Test
    fun `a snapshot survives the trip across the process boundary`() {
        val snap = WidgetSnapshot("EUR", 188_604, 180_000, 12, 20_400)
        assertEquals(snap, WidgetSnapshot.decode(snap.encode()))
    }

    @Test
    fun `no budget round-trips as no budget rather than as zero`() {
        // Zero is a budget of nothing, which would render as permanently over.
        val snap = WidgetSnapshot("INR", 4_000, null, 3, 20_400)
        val back = WidgetSnapshot.decode(snap.encode())
        assertNull(back?.budgetMinor)
        assertNull(back?.pressure())
    }

    @Test
    fun `a malformed payload yields no widget rather than a crash`() {
        // The widget may be running an older build than the app that wrote
        // this, and a crash lands on someone's home screen.
        assertNull(WidgetSnapshot.decode(null))
        assertNull(WidgetSnapshot.decode(""))
        assertNull(WidgetSnapshot.decode("EUR|188604|1|2"))
        assertNull(WidgetSnapshot.decode("EUR|lots|180000|12|20400"))
        assertNull(WidgetSnapshot.decode("|1|2|3|4"))
        assertNull(WidgetSnapshot.decode("EUR|1|2|3|4|5"))
    }

    @Test
    fun `the preferred currency wins`() {
        // Someone who has just paid an Indian bill wants rupees, and a widget
        // cannot be asked which currency it meant.
        val ledgers = listOf(ledger(Currency.EUR, 100), ledger(Currency.INR, 5_000, index = 1))
        assertEquals("INR", WidgetSnapshot.from(ledgers, Currency.INR, 0, 20_400)?.currencyCode)
    }

    @Test
    fun `an unknown preference falls back to the first ledger`() {
        val ledgers = listOf(ledger(Currency.EUR, 100))
        assertEquals("EUR", WidgetSnapshot.from(ledgers, Currency.JPY, 0, 20_400)?.currencyCode)
        assertEquals("EUR", WidgetSnapshot.from(ledgers, null, 0, 20_400)?.currencyCode)
    }

    @Test
    fun `an empty ledger publishes nothing at all`() {
        // Better a widget that says "open VITT" than one showing a confident zero.
        assertNull(WidgetSnapshot.from(emptyList(), Currency.EUR, 0, 20_400))
    }

    @Test
    fun `pressure passes one when over — without calling it a failure`() {
        assertEquals(1.5f, WidgetSnapshot("EUR", 150, 100, 0, 20_400).pressure())
        // A zero budget has no meaningful ratio and must not divide by zero.
        assertNull(WidgetSnapshot("EUR", 150, 0, 0, 20_400).pressure())
    }

    // --- the figure the widget exists for ---

    private fun snap(spent: Long, budget: Long?, monthEnd: Int) =
        WidgetSnapshot("EUR", spent, budget, 0, monthEnd)

    @Test
    fun `room per day divides what is left across the days that remain`() {
        // 400 left over 10 days inclusive, so 40 a day.
        val s = snap(spent = 60_000, budget = 100_000, monthEnd = 20_409)
        assertEquals(4_000L, s.roomPerDay(today = 20_400))
        assertEquals(10, s.daysLeft(today = 20_400))
    }

    @Test
    fun `a day spent nothing raises tomorrow's allowance`() {
        // The whole design. Restraint is rewarded by arithmetic rather than by
        // a badge, and the number moves at midnight without anyone opening the
        // app, which is what makes the widget worth a glance at all.
        val s = snap(spent = 60_000, budget = 100_000, monthEnd = 20_409)
        val today = s.roomPerDay(20_400)!!
        val tomorrow = s.roomPerDay(20_401)!!
        assertTrue(tomorrow > today, "spending nothing should leave more per day")
    }

    @Test
    fun `the last day of the month still has one day left — not zero`() {
        // Zero days would divide by zero and take the home screen with it.
        val s = snap(spent = 0, budget = 10_000, monthEnd = 20_400)
        assertEquals(1, s.daysLeft(today = 20_400))
        assertEquals(10_000L, s.roomPerDay(today = 20_400))
    }

    @Test
    fun `a day past the month end does not go negative`() {
        val s = snap(spent = 0, budget = 10_000, monthEnd = 20_400)
        assertEquals(1, s.daysLeft(today = 20_405))
        assertEquals(10_000L, s.roomPerDay(today = 20_405))
    }

    @Test
    fun `over budget has no allowance rather than a negative one`() {
        // Dressing a negative up as an allowance would be the app lying to
        // make the number look friendlier.
        assertNull(snap(spent = 120_000, budget = 100_000, monthEnd = 20_409).roomPerDay(20_400))
        // Exactly spent is also nothing left, not zero per day.
        assertNull(snap(spent = 100_000, budget = 100_000, monthEnd = 20_409).roomPerDay(20_400))
    }

    @Test
    fun `no budget means no allowance to divide`() {
        assertNull(snap(spent = 5_000, budget = null, monthEnd = 20_409).roomPerDay(20_400))
    }

    @Test
    fun `the month end survives the trip across the boundary`() {
        val s = snap(spent = 1, budget = 2, monthEnd = 20_409)
        assertEquals(20_409, WidgetSnapshot.decode(s.encode())?.monthEndDay)
    }
}
