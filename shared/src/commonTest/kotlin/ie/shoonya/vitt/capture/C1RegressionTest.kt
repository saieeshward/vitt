package ie.shoonya.vitt.capture

import ie.shoonya.vitt.money.Currency
import kotlin.test.Test
import kotlin.test.assertEquals

class C1RegressionTest {
    @Test
    fun `ungrouped four digit amounts must not be truncated`() {
        assertEquals(-150000L, AmountParser.parse("€1500 debited").amount?.minor)
        assertEquals(-120050L, AmountParser.parse("Payment of €1200.50 debited").amount?.minor)
        assertEquals(-2500000L, AmountParser.parse("Rs 25000 debited").amount?.minor)
    }
}

class DirectionSafetyTest {
    @Test
    fun `an unknown direction is never committable`() {
        // The dangerous case: a string containing both refund and purchase
        // wording. Guessing the sign here is an EUR 80 swing.
        val r = AmountParser.parse("Refund credited for your purchase of €40.00")
        assertEquals(Direction.UNKNOWN, r.direction)
        kotlin.test.assertFalse(r.isUsable, "must not be one-tap committable")
        assertEquals(4000L, r.magnitude?.minor, "magnitude is still offered")
    }

    @Test
    fun `a clear direction stays committable`() {
        val r = AmountParser.parse("You spent €12.50 at TESCO - debited")
        kotlin.test.assertTrue(r.isUsable)
        assertEquals(-1250L, r.amount?.minor)
    }
}
