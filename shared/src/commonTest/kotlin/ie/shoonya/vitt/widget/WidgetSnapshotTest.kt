package ie.shoonya.vitt.widget

import ie.shoonya.vitt.model.Ledger
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
        val snap = WidgetSnapshot("EUR", 188_604, 180_000, 12)
        assertEquals(snap, WidgetSnapshot.decode(snap.encode()))
    }

    @Test
    fun `no budget round-trips as no budget rather than as zero`() {
        // Zero is a budget of nothing, which would render as permanently over.
        val snap = WidgetSnapshot("INR", 4_000, null, 3)
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
        assertNull(WidgetSnapshot.decode("EUR|188604"))
        assertNull(WidgetSnapshot.decode("EUR|lots|180000|12"))
        assertNull(WidgetSnapshot.decode("|1|2|3"))
        assertNull(WidgetSnapshot.decode("EUR|1|2|3|4"))
    }

    @Test
    fun `the preferred currency wins`() {
        // Someone who has just paid an Indian bill wants rupees, and a widget
        // cannot be asked which currency it meant.
        val ledgers = listOf(ledger(Currency.EUR, 100), ledger(Currency.INR, 5_000, index = 1))
        assertEquals("INR", WidgetSnapshot.from(ledgers, Currency.INR, 0)?.currencyCode)
    }

    @Test
    fun `an unknown preference falls back to the first ledger`() {
        val ledgers = listOf(ledger(Currency.EUR, 100))
        assertEquals("EUR", WidgetSnapshot.from(ledgers, Currency.JPY, 0)?.currencyCode)
        assertEquals("EUR", WidgetSnapshot.from(ledgers, null, 0)?.currencyCode)
    }

    @Test
    fun `an empty ledger publishes nothing at all`() {
        // Better a widget that says "open VITT" than one showing a confident zero.
        assertNull(WidgetSnapshot.from(emptyList(), Currency.EUR, 0))
    }

    @Test
    fun `pressure passes one when over — without calling it a failure`() {
        assertEquals(1.5f, WidgetSnapshot("EUR", 150, 100, 0).pressure())
        // A zero budget has no meaningful ratio and must not divide by zero.
        assertNull(WidgetSnapshot("EUR", 150, 0, 0).pressure())
    }
}
