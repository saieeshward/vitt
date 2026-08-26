package ie.shoonya.vitt.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MoneyTest {

    @Test
    fun `formats minor units as decimal`() {
        assertEquals("12.50", Money(1250, Currency.EUR).toPlainString())
        assertEquals("0.05", Money(5, Currency.EUR).toPlainString())
        assertEquals("-12.50", Money(-1250, Currency.EUR).toPlainString())
        assertEquals("1000.00", Money(100_000, Currency.EUR).toPlainString())
    }

    @Test
    fun `respects currencies with no minor unit`() {
        // The classic scaling bug: treating JPY like EUR divides by 100.
        assertEquals("500", Money(500, Currency.JPY).toPlainString())
    }

    @Test
    fun `parses plain decimals`() {
        assertEquals(Money(1250, Currency.EUR), Money.ofPlain("12.50", Currency.EUR))
        assertEquals(Money(1250, Currency.EUR), Money.ofPlain("12.5", Currency.EUR))
        assertEquals(Money(-1250, Currency.EUR), Money.ofPlain("-12.50", Currency.EUR))
        assertEquals(Money(1200, Currency.EUR), Money.ofPlain("12", Currency.EUR))
    }

    @Test
    fun `refuses more precision than the currency has`() {
        assertFailsWith<IllegalArgumentException> { Money.ofPlain("12.505", Currency.EUR) }
    }

    @Test
    fun `refuses to add different currencies`() {
        val e = assertFailsWith<IllegalArgumentException> {
            Money(1000, Currency.EUR) + Money(1000, Currency.INR)
        }
        assertTrue(e.message!!.contains("Transfer"), "should point at the Transfer type")
    }

    @Test
    fun `splitting never creates or destroys money`() {
        // 10.00 three ways cannot be done exactly; the remainder must still balance.
        val total = Money(1000, Currency.EUR)
        val parts = total.splitEvenly(3)
        assertEquals(3, parts.size)
        assertEquals(total.minor, parts.sumOf { it.minor })
        assertEquals(listOf(334L, 333L, 333L), parts.map { it.minor })
    }

    @Test
    fun `splitting a negative amount still balances`() {
        val total = Money(-1000, Currency.EUR)
        val parts = total.splitEvenly(3)
        assertEquals(total.minor, parts.sumOf { it.minor })
    }

    @Test
    fun `no floating point drift over many additions`() {
        // 0.1 summed 1000 times is exactly 100.00 in integer minor units, and
        // famously is not with Double.
        var acc = Money(0, Currency.EUR)
        repeat(1000) { acc += Money(10, Currency.EUR) }
        assertEquals("100.00", acc.toPlainString())
    }
}

class MoneyDisplayTest {

    @Test
    fun `every figure carries its own currency symbol`() {
        // There is no default currency in this app, so a bare number is always
        // ambiguous — the UI must never be able to render one.
        assertEquals("€12.50", Money(1250, Currency.EUR).display())
        assertEquals("₹500.00", Money(50000, Currency.INR).display())
        assertEquals("¥500", Money(500, Currency.JPY).display())
    }

    @Test
    fun `negatives keep the sign outside the symbol`() {
        assertEquals("-€12.50", Money(-1250, Currency.EUR).display())
    }

    @Test
    fun `grouping follows the currency — not the locale`() {
        // Indian grouping is 2-2-3; Western grouping on a rupee figure reads as
        // careless to anyone who uses rupees.
        assertEquals("₹1,23,456.78", Money(12345678, Currency.INR).display())
        assertEquals("€1,234.56", Money(123456, Currency.EUR).display())
    }

    @Test
    fun `zero renders as zero — not as empty`() {
        assertEquals("€0.00", Money(0, Currency.EUR).display())
    }
}

class MoneyParsingSafetyTest {

    @Test
    fun `non-ASCII digits are refused rather than parsed differently per platform`() {
        // Char.isDigit() is Unicode-aware, so these passed validation — and then
        // String.toLong() behaved differently on JVM and Native, so the same
        // pasted amount parsed on Android and threw on iOS.
        assertFailsWith<IllegalArgumentException> { Money.ofPlain("١٢.٥٠", Currency.EUR) }
        assertFailsWith<IllegalArgumentException> { Money.ofPlain("१२.५०", Currency.INR) }
    }

    @Test
    fun `malformed decimals raise the documented exception type`() {
        assertFailsWith<IllegalArgumentException> { Money.ofPlain("12..50", Currency.EUR) }
        assertFailsWith<IllegalArgumentException> { Money.ofPlain("1..2", Currency.EUR) }
        assertFailsWith<IllegalArgumentException> { Money.ofPlain("", Currency.EUR) }
    }
}
