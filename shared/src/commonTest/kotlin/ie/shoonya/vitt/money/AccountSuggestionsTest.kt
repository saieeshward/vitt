package ie.shoonya.vitt.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountSuggestionsTest {

    @Test
    fun `every currency the app knows offers something to tap`() {
        // The setup screen has no other fast path. A currency that fell through
        // to an empty chip row would put the person back on the keyboard, which
        // is the one thing the screen exists to avoid.
        Currency.entries.forEach { currency ->
            assertTrue(
                AccountSuggestions.forCurrency(currency).isNotEmpty(),
                "no suggestions for ${currency.code}",
            )
        }
    }

    @Test
    fun `the generic names are always reachable`() {
        // Someone whose bank is not listed still has four chips, so an unlisted
        // bank costs one typed name rather than five.
        Currency.entries.forEach { currency ->
            val names = AccountSuggestions.forCurrency(currency)
            listOf("Current", "Savings", "Card", "Cash").forEach {
                assertTrue(it in names, "${currency.code} is missing $it")
            }
        }
    }

    @Test
    fun `local names come before the generic ones`() {
        // Order is the whole design: the chips someone actually recognises have
        // to be the ones they see without scrolling the row.
        val eur = AccountSuggestions.forCurrency(Currency.EUR)
        assertEquals("AIB", eur.first())
        assertTrue(eur.indexOf("AIB") < eur.indexOf("Cash"))

        val inr = AccountSuggestions.forCurrency(Currency.INR)
        assertEquals("HDFC", inr.first())
        assertTrue(inr.indexOf("HDFC") < inr.indexOf("Cash"))
    }

    @Test
    fun `a name is never offered twice`() {
        // Two identical chips would both add, and the second would look broken
        // because the screen refuses a duplicate within one currency.
        Currency.entries.forEach { currency ->
            val names = AccountSuggestions.forCurrency(currency)
            assertEquals(names.size, names.toSet().size, "${currency.code} repeats a name")
        }
    }
}
