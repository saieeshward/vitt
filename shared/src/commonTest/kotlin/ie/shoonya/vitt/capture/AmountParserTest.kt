package ie.shoonya.vitt.capture

import ie.shoonya.vitt.money.Currency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AmountParserTest {

    // ---- EUR ---------------------------------------------------------------

    @Test
    fun `revolut style debit`() {
        val r = AmountParser.parse("You spent €12.50 at TESCO STORES 3421 DUBLIN")
        assertEquals(-1250L, r.amount?.minor)
        assertEquals(Currency.EUR, r.amount?.currency)
        assertEquals(Direction.OUTFLOW, r.direction)
        assertEquals(Confidence.HIGH, r.confidence)
    }

    @Test
    fun `code before amount`() {
        val r = AmountParser.parse("EUR 45.00 was debited from your account ending 1234")
        assertEquals(-4500L, r.amount?.minor)
        assertEquals(Direction.OUTFLOW, r.direction)
    }

    @Test
    fun `symbol after amount, continental grouping`() {
        // German/Dutch/Irish-bank formatting: comma is the decimal point here.
        val r = AmountParser.parse("Sie haben 12,50 € bezahlt - debit")
        assertEquals(-1250L, r.amount?.minor)
    }

    @Test
    fun `thousands separator with decimals`() {
        val r = AmountParser.parse("Payment of €1,234.56 debited")
        assertEquals(-123456L, r.amount?.minor)
    }

    @Test
    fun `continental thousands and decimals`() {
        val r = AmountParser.parse("Zahlung von 1.234,56 € debit")
        assertEquals(-123456L, r.amount?.minor)
    }

    // ---- INR ---------------------------------------------------------------

    @Test
    fun `hdfc style upi debit`() {
        val r = AmountParser.parse(
            "Rs.500.00 debited from A/c XX1234 to VPA merchant@okhdfcbank on 25-08-26"
        )
        assertEquals(-50000L, r.amount?.minor)
        assertEquals(Currency.INR, r.amount?.currency)
        assertEquals(Direction.OUTFLOW, r.direction)
    }

    @Test
    fun `rupee symbol without decimals`() {
        val r = AmountParser.parse("₹500 debited")
        assertEquals(-50000L, r.amount?.minor)
    }

    @Test
    fun `lakh grouping is not western grouping`() {
        // 1,23,456.78 is 123456.78 - two-digit groups break naive thousands regexes.
        val r = AmountParser.parse("Rs 1,23,456.78 credited to your account")
        assertEquals(12345678L, r.amount?.minor)
        assertEquals(Direction.INFLOW, r.direction)
    }

    @Test
    fun `trailing slash dash notation`() {
        val r = AmountParser.parse("Amount 500/- debited towards electricity bill")
        assertEquals(-50000L, r.amount?.minor)
    }

    // ---- direction safety --------------------------------------------------

    @Test
    fun `refund is an inflow`() {
        val r = AmountParser.parse("Refund of €20.00 credited to your card")
        assertEquals(2000L, r.amount?.minor)
        assertEquals(Direction.INFLOW, r.direction)
    }

    @Test
    fun `conflicting direction words refuse to guess`() {
        // "refund ... for a purchase" contains both families. Guessing here is
        // how a EUR 40 refund becomes a EUR 40 expense.
        val r = AmountParser.parse("Refund credited for your purchase of €40.00")
        assertEquals(Direction.UNKNOWN, r.direction)
        assertEquals(Confidence.LOW, r.confidence, "must go to the review queue")
    }

    @Test
    fun `no direction words at all is low confidence`() {
        val r = AmountParser.parse("€12.50 TESCO")
        assertEquals(Direction.UNKNOWN, r.direction)
        assertEquals(Confidence.LOW, r.confidence)
    }

    // ---- ambiguity and failure ---------------------------------------------

    @Test
    fun `three digits after a separator is flagged ambiguous`() {
        // '1.234' is 1234 in Germany and 1.234 in Ireland. Unresolvable.
        val r = AmountParser.parse("Payment of €1.234 debited")
        assertEquals(Confidence.LOW, r.confidence)
        assertTrue(r.warnings.any { it.contains("ambiguous") }, r.warnings.toString())
    }

    @Test
    fun `picks the amount next to the currency, not the biggest number`() {
        val r = AmountParser.parse("Rs.500 debited at store 987654")
        assertEquals(-50000L, r.amount?.minor)
    }

    @Test
    fun `truncated text still yields the amount`() {
        val r = AmountParser.parse("You spent €12.50 at TESCO STORES DUBLIN CITY CENT…")
        assertEquals(-1250L, r.amount?.minor)
        assertTrue(r.warnings.any { it.contains("truncated") })
    }

    @Test
    fun `no currency means no amount`() {
        val r = AmountParser.parse("You spent 12.50 somewhere - debited")
        assertNull(r.amount)
        assertEquals(Confidence.LOW, r.confidence)
    }

    @Test
    fun `default currency rescues a bare number`() {
        val r = AmountParser.parse("You spent 12.50 - debited", defaultCurrency = Currency.EUR)
        assertEquals(-1250L, r.amount?.minor)
    }

    @Test
    fun `empty and junk input is handled, not crashed`() {
        assertNull(AmountParser.parse("").amount)
        assertNull(AmountParser.parse("    ").amount)
        assertNull(AmountParser.parse("hello world").amount)
    }

    @Test
    fun `non breaking space before symbol does not break parsing`() {
        val r = AmountParser.parse("Debit of 12,50 € processed")
        assertEquals(-1250L, r.amount?.minor)
    }

    // ---- merchant ----------------------------------------------------------

    @Test
    fun `extracts and tidies a merchant`() {
        val r = AmountParser.parse("You spent €12.50 at TESCO STORES 3421 - debited")
        assertNotNull(r.merchant)
        assertTrue(r.merchant!!.contains("Tesco", ignoreCase = true), "got '${r.merchant}'")
    }

    @Test
    fun `extracts a upi vpa as merchant`() {
        val r = AmountParser.parse("Rs.250 debited to VPA chaiwala@okaxis")
        assertNotNull(r.merchant)
        assertTrue(r.merchant!!.contains("chaiwala", ignoreCase = true), "got '${r.merchant}'")
    }
}
