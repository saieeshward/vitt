package ie.shoonya.vitt.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RateTest {

    /** EUR 500.00 became INR 45,000.00 — so 90 INR to the euro. */
    private val eurToInr = Rate(Currency.EUR, Currency.INR, sentMinor = 50_000, receivedMinor = 4_500_000)

    @Test
    fun `a rate reads as units received per unit sent`() {
        assertEquals("90.000000", eurToInr.toPlainString())
    }

    @Test
    fun `precision is requestable`() {
        assertEquals("90", eurToInr.toPlainString(decimals = 0))
        assertEquals("90.00", eurToInr.toPlainString(decimals = 2))
    }

    @Test
    fun `the inverse is the same movement read backwards`() {
        val inverse = eurToInr.inverse()
        assertEquals(Currency.INR, inverse.from)
        assertEquals(Currency.EUR, inverse.to)
        assertEquals("0.011111", inverse.toPlainString())
    }

    @Test
    fun `a small inverse rate keeps its significant figures`() {
        // The reason DEFAULT_DECIMALS is 6: at four places this would be 0.0111,
        // losing two significant figures on the pair the app cares most about.
        val s = eurToInr.inverse().toPlainString(decimals = 4)
        assertEquals("0.0111", s)
    }

    @Test
    fun `a currency with no minor unit is not scaled by a hundred`() {
        // EUR 100.00 → JPY 17,000. JPY has exponent 0, so 17_000 minor units are
        // JPY 17,000, not JPY 170. Dropping the exponent gives a plausible-looking
        // rate that is wrong by two orders of magnitude.
        val eurToJpy = Rate(Currency.EUR, Currency.JPY, sentMinor = 10_000, receivedMinor = 17_000)
        assertEquals("170.000000", eurToJpy.toPlainString())
    }

    @Test
    fun `a rate out of a currency with no minor unit is also correct`() {
        // JPY 17,000 → EUR 100.00, the same movement in reverse.
        val jpyToEur = Rate(Currency.JPY, Currency.EUR, sentMinor = 17_000, receivedMinor = 10_000)
        assertEquals("0.005882", jpyToEur.toPlainString())
    }

    @Test
    fun `a non-terminating rate rounds rather than drifting`() {
        // 100 sent, 33.33 received: 1/3, which no binary float holds exactly.
        val third = Rate(Currency.EUR, Currency.USD, sentMinor = 10_000, receivedMinor = 3_333)
        assertEquals("0.333300", third.toPlainString())
    }

    @Test
    fun `rounding is half away from zero`() {
        // 3 sent → 2 received is 0.6666…, so the sixth place rounds up.
        val r = Rate(Currency.EUR, Currency.USD, sentMinor = 3, receivedMinor = 2)
        assertEquals("0.666667", r.toPlainString())
    }

    @Test
    fun `a rate derived from a large transfer stays representable`() {
        // INR 10,000,000.00 → EUR 111,000.00. The gcd reduction is what keeps
        // this in range; multiplying naively would overflow.
        val r = Rate(Currency.INR, Currency.EUR, sentMinor = 1_000_000_000, receivedMinor = 11_100_000)
        assertEquals("0.011100", r.toPlainString())
    }

    @Test
    fun `an unrepresentable rate is null rather than a wrapped number`() {
        val r = Rate(Currency.EUR, Currency.INR, sentMinor = 1, receivedMinor = Long.MAX_VALUE)
        assertNull(r.scaledBy(18))
    }

    @Test
    fun `a representable rate is not null`() {
        assertNotNull(eurToInr.scaledBy(6))
    }

    @Test
    fun `a rate needs two positive legs`() {
        assertFailsWith<IllegalArgumentException> {
            Rate(Currency.EUR, Currency.INR, sentMinor = 0, receivedMinor = 100)
        }
        assertFailsWith<IllegalArgumentException> {
            Rate(Currency.EUR, Currency.INR, sentMinor = 100, receivedMinor = -1)
        }
    }

    @Test
    fun `negative precision is rejected`() {
        assertFailsWith<IllegalArgumentException> { eurToInr.scaledBy(-1) }
    }

    @Test
    fun `a rate between two same-exponent currencies needs no exponent correction`() {
        // GBP 100.00 → EUR 118.50, both exponent 2.
        val r = Rate(Currency.GBP, Currency.EUR, sentMinor = 10_000, receivedMinor = 11_850)
        assertEquals("1.185000", r.toPlainString())
    }

    @Test
    fun `inverting twice returns the original`() {
        assertEquals(eurToInr, eurToInr.inverse().inverse())
    }
}
