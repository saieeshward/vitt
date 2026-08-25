package ie.shoonya.vitt.money

import kotlin.test.Test
import kotlin.test.assertEquals

class AmountEntryTest {

    private fun type(entry: AmountEntry, keys: String) =
        keys.fold(entry) { acc, c -> acc.press(c) }

    @Test
    fun `right to left entry fills minor units first`() {
        var e = AmountEntry()
        assertEquals("€0.00", e.display())
        e = e.press('1'); assertEquals("€0.01", e.display())
        e = e.press('2'); assertEquals("€0.12", e.display())
        e = e.press('5'); assertEquals("€1.25", e.display())
        e = e.press('0'); assertEquals("€12.50", e.display())
        assertEquals(1250L, e.money.minor)
    }

    @Test
    fun `leading zeros are ignored`() {
        val e = type(AmountEntry(), "0001250")
        assertEquals(1250L, e.money.minor)
    }

    @Test
    fun `backspace removes one digit`() {
        var e = type(AmountEntry(), "1250")
        e = e.backspace()
        assertEquals("€1.25", e.display())
        e = e.clear()
        assertEquals("€0.00", e.display())
        // Backspacing an empty entry is a no-op, not a crash.
        assertEquals("€0.00", e.backspace().display())
    }

    @Test
    fun `western thousands grouping`() {
        assertEquals("€1,234.56", type(AmountEntry(), "123456").display())
        assertEquals("€12,345.67", type(AmountEntry(), "1234567").display())
    }

    @Test
    fun `indian grouping is two-two-three`() {
        // 1,23,456.78 - not 123,456.78
        val e = type(AmountEntry(currency = Currency.INR), "12345678")
        assertEquals("₹1,23,456.78", e.display())
    }

    @Test
    fun `currency without minor units shows no decimals`() {
        val e = type(AmountEntry(currency = Currency.JPY), "500")
        assertEquals("¥500", e.display())
        assertEquals(500L, e.money.minor)
    }

    @Test
    fun `digit cap prevents runaway input`() {
        val e = type(AmountEntry(), "9".repeat(30))
        assertEquals(AmountEntry.MAX_DIGITS, e.digits.length)
    }

    @Test
    fun `round trips from an existing amount`() {
        val original = Money(-1250, Currency.EUR)
        val entry = AmountEntry.of(original)
        assertEquals("€12.50", entry.display())
        assertEquals(1250L, entry.money.minor, "sign is held outside the keypad")
    }

    @Test
    fun `switching currency reinterprets the digits`() {
        val e = type(AmountEntry(), "1250").withCurrency(Currency.INR)
        assertEquals("₹12.50", e.display())
    }
}
