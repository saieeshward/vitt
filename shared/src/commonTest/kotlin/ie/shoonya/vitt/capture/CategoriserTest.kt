package ie.shoonya.vitt.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CategoriserTest {

    // --- merchant normalisation -------------------------------------------

    @Test
    fun `an acquirer string reduces to a brand`() {
        assertEquals("Tesco", MerchantName.clean("TESCO STORES 3421 DUBLIN IE"))
    }

    @Test
    fun `store numbers and terminal ids are dropped`() {
        assertEquals("Spar", MerchantName.clean("SPAR 00417 T4419"))
        assertEquals("Centra", MerchantName.clean("POS CENTRA STR0031"))
    }

    @Test
    fun `an aggregator prefix is dropped`() {
        assertEquals("Coffee Angel", MerchantName.clean("SQ *COFFEE ANGEL"))
        assertEquals("Bookshop", MerchantName.clean("PAYPAL *BOOKSHOP"))
    }

    @Test
    fun `two-letter country codes go but real two-letter brands stay`() {
        assertEquals("Tesco", MerchantName.clean("TESCO IE"))
        assertEquals("O2", MerchantName.clean("O2 IE"))
    }

    @Test
    fun `zero-width characters do not fork one merchant into two`() {
        // These travel in copied bank text and are invisible on screen.
        assertEquals(MerchantName.key("TESCO"), MerchantName.key("TES​CO"))
    }

    @Test
    fun `a string of only noise yields nothing rather than junk`() {
        // Junk that looks like data is worse than an admitted blank.
        assertNull(MerchantName.clean("POS 4419 XXXX IE"))
        // A leading country code is not a brand either.
        assertNull(MerchantName.clean("IE 4419"))
        assertNull(MerchantName.clean("   "))
    }

    @Test
    fun `the display form and the matching key are separate`() {
        // So that nicer casing later does not invalidate every learned rule.
        assertEquals("Tesco", MerchantName.clean("TESCO STORES DUBLIN"))
        assertEquals("tesco", MerchantName.key("TESCO STORES DUBLIN"))
    }

    @Test
    fun `only the first three words survive`() {
        // Acquirer strings run brand-then-location, so the tail is noise.
        assertEquals("aer lingus airport", MerchantName.key("AER LINGUS DUBLIN AIRPORT COUNTY DUBLIN"))
    }

    // --- tier 2, shipped seeds ---------------------------------------------

    @Test
    fun `a seed keyword categorises and says so`() {
        val result = assertNotNull(Categoriser.seedsOnly().categorise("TESCO STORES 3421 DUBLIN"))
        assertEquals(Category.GROCERIES, result.category)
        assertEquals(CategorySource.SEED, result.source)
    }

    @Test
    fun `seeds cover both markets`() {
        val c = Categoriser.seedsOnly()
        assertEquals(Category.DINING, c.categorise("SWIGGY BANGALORE")?.category)
        assertEquals(Category.TRANSPORT, c.categorise("DUBLINBUS")?.category)
        assertEquals(Category.UTILITIES, c.categorise("AIRTEL PREPAID")?.category)
        assertEquals(Category.HEALTH, c.categorise("BOOTS PHARMACY")?.category)
    }

    @Test
    fun `a run-together merchant still matches`() {
        // Acquirers routinely drop the space.
        assertEquals(Category.SHOPPING, Categoriser.seedsOnly().categorise("AMAZONIN")?.category)
    }

    @Test
    fun `a short keyword never matches as a substring`() {
        // `ola` inside `chocolate` would otherwise book a Transport charge.
        assertNull(Categoriser.seedsOnly().categorise("CHOCOLATERIE")?.category)
        // But it still matches as a whole word.
        assertEquals(Category.TRANSPORT, Categoriser.seedsOnly().categorise("OLA CABS")?.category)
    }

    @Test
    fun `an unknown merchant is not guessed at`() {
        // §6's third tier is to ask. A wrong guess distorts a budget silently.
        assertNull(Categoriser.seedsOnly().categorise("KILKENNY DESIGN"))
    }

    @Test
    fun `no merchant means nothing to categorise`() {
        val c = Categoriser.seedsOnly()
        assertNull(c.categorise(null))
        assertNull(c.categorise(""))
        assertNull(c.categorise("   "))
    }

    // --- tier 1, learned rules --------------------------------------------

    @Test
    fun `a learned rule beats a seed`() {
        // Tesco seeds to Groceries; this user buys lunch there.
        val c = Categoriser(mapOf("tesco" to Category.DINING))
        val result = assertNotNull(c.categorise("TESCO STORES 3421 DUBLIN"))
        assertEquals(Category.DINING, result.category)
        assertEquals(CategorySource.LEARNED, result.source)
    }

    @Test
    fun `a learned rule generalises to another branch`() {
        val c = Categoriser(mapOf("tesco" to Category.DINING))
        assertEquals(Category.DINING, c.categorise("TESCO EXPRESS 88 CORK")?.category)
    }

    @Test
    fun `the most specific learned rule wins`() {
        val c = Categoriser(
            mapOf(
                "tesco" to Category.GROCERIES,
                "tesco express" to Category.DINING,
            ),
        )
        assertEquals(Category.DINING, c.categorise("TESCO EXPRESS 88 CORK")?.category)
        assertEquals(Category.GROCERIES, c.categorise("TESCO SUPERSTORE NAAS")?.category)
    }

    @Test
    fun `a merchant a person typed is left alone`() {
        // Normalising typed text does damage: the stop-word list ate the middle
        // of "Taxi to airport", and title-casing produced "Dinner With Anya".
        assertEquals("Taxi to airport", MerchantName.clean("Taxi to airport"))
        assertEquals("Dinner with Anya", MerchantName.clean("Dinner with Anya"))
        assertEquals("Tesco", MerchantName.clean("Tesco"))
    }

    @Test
    fun `typed and machine forms of one merchant share a rule key`() {
        // Display differs; matching must not, or a rule taught on a typed entry
        // would not fire on the card feed.
        assertEquals(
            MerchantName.key("Tesco"),
            MerchantName.key("TESCO STORES 3421 DUBLIN IE"),
        )
    }

    @Test
    fun `a merchant named after a city keeps its name`() {
        // Kilkenny Design and Cork Cheese are real. A city is only stripped once
        // a brand is already in hand, because acquirer strings run brand-first.
        assertEquals("Kilkenny Design Centre", MerchantName.clean("KILKENNY DESIGN CENTRE DUBLIN"))
        assertEquals("Cork Cheese", MerchantName.clean("CORK CHEESE CO CORK"))
        // And a trailing city is still noise.
        assertEquals("Tesco", MerchantName.clean("TESCO DUBLIN"))
    }

    @Test
    fun `a learned rule works for a merchant no seed knows`() {
        val c = Categoriser(mapOf("kilkenny design" to Category.SHOPPING))
        assertEquals(Category.SHOPPING, c.categorise("KILKENNY DESIGN CENTRE")?.category)
    }

    @Test
    fun `rules match on text and so work in any currency`() {
        // The same rule, and nothing about it refers to a currency.
        val c = Categoriser(mapOf("uber" to Category.TRANSPORT))
        assertEquals(Category.TRANSPORT, c.categorise("UBER BV AMSTERDAM")?.category)
        assertEquals(Category.TRANSPORT, c.categorise("UBER INDIA BANGALORE")?.category)
    }

    @Test
    fun `one definition of the same merchant — used everywhere`() {
        // The categoriser and "which past entries would this reach" used to
        // disagree — prefixes versus exact equality — so a rule taught at
        // TESCO DUBLIN silently failed to offer to fix TESCO NAAS.
        assertTrue(Categoriser.applies("tesco", "tesco"))
        assertTrue(Categoriser.applies("tesco", "tesco naas"))
        assertTrue(Categoriser.applies("tesco naas", "tesco"))
        assertFalse(Categoriser.applies("tesco", "tescoland"))
        assertFalse(Categoriser.applies("tesco", "lidl"))
        assertFalse(Categoriser.applies("", "tesco"))
    }

    // --- the taxonomy ------------------------------------------------------

    @Test
    fun `the taxonomy is the fourteen the plan locks`() {
        assertEquals(14, Category.entries.size)
        assertEquals(
            listOf(
                "Groceries", "Dining", "Transport", "Housing", "Utilities", "Health",
                "Shopping", "Entertainment", "Travel", "Family & Gifts",
                "Subscriptions", "Income", "Transfer", "Miscellaneous",
            ),
            Category.entries.map { it.label },
        )
    }

    @Test
    fun `category codes are stable`() {
        // Persisted into the log and read by other devices, so renaming an enum
        // constant must not change them.
        Category.entries.forEach { assertEquals(it, Category.ofCode(it.code)) }
    }

    @Test
    fun `older display-name spellings still resolve`() {
        // Rows written before the taxonomy was codified are already in sheets.
        assertEquals(Category.GROCERIES, Category.ofCode("Groceries"))
        assertEquals(Category.FAMILY_GIFTS, Category.ofCode("Family"))
        assertEquals(Category.MISCELLANEOUS, Category.ofCode("Other"))
        assertEquals(Category.DINING, Category.ofCode("Food"))
    }

    @Test
    fun `an unknown category code does not resolve`() {
        assertNull(Category.ofCode("crypto"))
        assertNull(Category.ofCode(""))
    }

    @Test
    fun `income and transfer are not spending categories`() {
        assertEquals(
            listOf(Category.INCOME, Category.TRANSFER),
            Category.entries.filterNot { it.isSpending },
        )
    }

    @Test
    fun `every seed keyword names a real category`() {
        Categoriser.SEED.forEach { (keyword, category) ->
            assertEquals(category, Category.ofCode(category.code), "bad seed for $keyword")
        }
    }

    @Test
    fun `short seed keywords cannot fire as substrings`() {
        // Several real brands are three letters — eir, esb, jio, vhi — so the
        // guarantee to assert is behavioural, not a hardcoded list: a short
        // keyword buried inside a longer word must never categorise.
        val short = Categoriser.SEED.keys.filter { it.length < 4 }
        val c = Categoriser.seedsOnly()
        short.forEach { keyword ->
            assertNull(
                c.categorise("zz${keyword}zz"),
                "$keyword fired as a substring",
            )
        }
        // And each still works as a word on its own.
        short.forEach { keyword ->
            assertNotNull(c.categorise(keyword), "$keyword stopped matching as a word")
        }
    }
}
