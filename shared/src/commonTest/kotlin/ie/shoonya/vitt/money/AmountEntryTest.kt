package ie.shoonya.vitt.money

import kotlin.test.Test
import kotlin.test.assertEquals

class AmountEntryTest {

    private fun type(entry: AmountEntry, keys: String) =
        keys.fold(entry) { acc, c -> acc.press(c) }

    @Test
    fun `whole units first — typing 25 means twenty five`() {
        var e = AmountEntry()
        assertEquals("€0.00", e.display())
        e = e.press('2'); assertEquals("€2", e.display())
        e = e.press('5'); assertEquals("€25", e.display())
        assertEquals(2500L, e.money.minor)
    }

    @Test
    fun `the decimal key opens the fraction and shows where you are`() {
        var e = type(AmountEntry(), "25")
        e = e.press('.'); assertEquals("€25.", e.display())
        e = e.press('5'); assertEquals("€25.5", e.display())
        assertEquals(2550L, e.money.minor)
        e = e.press('0'); assertEquals("€25.50", e.display())
        assertEquals(2550L, e.money.minor)
    }

    @Test
    fun `a second decimal point and a third fraction digit are refused`() {
        val e = type(AmountEntry(), "1.2.3")
        assertEquals("€1.23", e.display())
        assertEquals(123L, e.money.minor)
    }

    @Test
    fun `the decimal key on an empty entry starts from zero`() {
        val e = type(AmountEntry(), ".5")
        assertEquals("€0.5", e.display())
        assertEquals(50L, e.money.minor)
    }

    @Test
    fun `a lone leading zero is a placeholder`() {
        assertEquals(500L, type(AmountEntry(), "05").money.minor)
        assertEquals("€0.05", type(AmountEntry(), "0.05").display())
    }

    @Test
    fun `backspace removes one key`() {
        var e = type(AmountEntry(), "12.5")
        e = e.backspace(); assertEquals("€12.", e.display())
        e = e.backspace(); assertEquals("€12", e.display())
        e = e.clear(); assertEquals("€0.00", e.display())
        // Backspacing an empty entry is a no-op, not a crash.
        assertEquals("€0.00", e.backspace().display())
    }

    @Test
    fun `western thousands grouping`() {
        assertEquals("€1,234.56", type(AmountEntry(), "1234.56").display())
        assertEquals("€12,345", type(AmountEntry(), "12345").display())
    }

    @Test
    fun `indian grouping is two-two-three`() {
        // 1,23,456.78 - not 123,456.78
        val e = type(AmountEntry(currency = Currency.INR), "123456.78")
        assertEquals("₹1,23,456.78", e.display())
    }

    @Test
    fun `currency without minor units has no decimal key`() {
        val e = type(AmountEntry(currency = Currency.JPY), "5.00")
        assertEquals("¥500", e.display())
        assertEquals(500L, e.money.minor)
    }

    @Test
    fun `digit cap prevents runaway input`() {
        val e = type(AmountEntry(), "9".repeat(30))
        assertEquals(AmountEntry.MAX_WHOLE_DIGITS, e.text.length)
    }

    @Test
    fun `takes the magnitude of an existing amount — the sign stays outside`() {
        val original = Money(-1250, Currency.EUR)
        val entry = AmountEntry.ofMagnitude(original)
        assertEquals("€12.5", entry.display())
        assertEquals("€12.50", entry.display(full = true))
        assertEquals(1250L, entry.money.minor, "sign is held outside the keypad")
        assertEquals("€1,500", AmountEntry.ofMagnitude(Money(150000, Currency.EUR)).display())
    }

    @Test
    fun `switching currency keeps the figure and drops what it cannot hold`() {
        val e = type(AmountEntry(), "12.50")
        assertEquals("₹12.50", e.withCurrency(Currency.INR).display())
        assertEquals("¥12", e.withCurrency(Currency.JPY).display())
        assertEquals(12L, e.withCurrency(Currency.JPY).money.minor)
    }

    @Test
    fun `stored amounts always show full precision`() {
        assertEquals("€25.00", Money(2500, Currency.EUR).displayUnsigned())
        assertEquals("+€3,410.00", Money(341000, Currency.EUR).display())
        assertEquals("€0.00", Money(0, Currency.EUR).display())
        assertEquals("+¥500", Money(500, Currency.JPY).display())
    }
}
