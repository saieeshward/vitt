package ie.shoonya.vitt.model

import ie.shoonya.vitt.capture.Category
import ie.shoonya.vitt.capture.CategorySource
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.sync.EventStore
import ie.shoonya.vitt.sync.testDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryRuleTest {

    private val node = "a219e7a71cc18912"

    private fun repo(): LedgerRepository {
        var t = 1_000L
        return LedgerRepository(EventStore.open(testDriver(), node) { t++ }) { t }
    }

    private fun euro(minor: Long) = Money(minor, Currency.EUR)

    @Test
    fun `a seed keyword categorises on capture and records the tier`() {
        val r = repo()
        r.record("t1", euro(-6820), day = 20_000, merchant = "TESCO STORES 3421 DUBLIN")
        val t = r.transactions().single()
        assertEquals(Category.GROCERIES, t.categoryOrNull)
        assertEquals(CategorySource.SEED, t.categorySource)
    }

    @Test
    fun `an unknown merchant is left uncategorised rather than guessed`() {
        val r = repo()
        r.record("t1", euro(-2000), day = 20_000, merchant = "SOME LOCAL PLACE")
        val t = r.transactions().single()
        assertNull(t.category)
        assertNull(t.categorySource)
    }

    @Test
    fun `an explicit category is kept and marked manual`() {
        val r = repo()
        r.record("t1", euro(-6820), day = 20_000, merchant = "TESCO", category = "Dining")
        val t = r.transactions().single()
        assertEquals(Category.DINING, t.categoryOrNull)
        assertEquals(CategorySource.MANUAL, t.categorySource)
    }

    @Test
    fun `a category is stored as its code — not its label`() {
        val r = repo()
        r.record("t1", euro(-6820), day = 20_000, category = "Groceries")
        assertEquals("groceries", r.transactions().single().category)
    }

    @Test
    fun `correcting a category teaches a rule`() {
        // §6's third tier: ask, then write a tier-1 rule. The correction should
        // never need making twice.
        val r = repo()
        r.record("t1", euro(-6820), day = 20_000, merchant = "TESCO STORES 3421 DUBLIN")
        r.categorise("t1", Category.DINING)

        assertEquals(mapOf("tesco" to Category.DINING), r.categoryRules())

        r.record("t2", euro(-1200), day = 20_001, merchant = "TESCO EXPRESS CORK")
        val next = r.transactions().first { it.id == "t2" }
        assertEquals(Category.DINING, next.categoryOrNull)
        assertEquals(CategorySource.LEARNED, next.categorySource)
    }

    @Test
    fun `the corrected transaction itself reads as manual`() {
        // Whatever fires on the next one, this category was chosen by hand.
        val r = repo()
        r.record("t1", euro(-6820), day = 20_000, merchant = "TESCO")
        r.categorise("t1", Category.DINING)
        val t = r.transactions().single()
        assertEquals(Category.DINING, t.categoryOrNull)
        assertEquals(CategorySource.MANUAL, t.categorySource)
    }

    @Test
    fun `teaching can be declined`() {
        val r = repo()
        r.record("t1", euro(-6820), day = 20_000, merchant = "TESCO")
        r.categorise("t1", Category.DINING, teach = false)
        assertTrue(r.categoryRules().isEmpty())
        assertEquals(Category.DINING, r.transactions().single().categoryOrNull)
    }

    @Test
    fun `correcting a transaction with no merchant still sets the category`() {
        // There is simply nothing to key a rule to, which is ordinary rather
        // than an error.
        val r = repo()
        r.record("t1", euro(-2000), day = 20_000)
        r.categorise("t1", Category.HEALTH)
        assertEquals(Category.HEALTH, r.transactions().single().categoryOrNull)
        assertTrue(r.categoryRules().isEmpty())
    }

    @Test
    fun `teaching the same merchant twice edits one rule`() {
        val r = repo()
        r.record("t1", euro(-6820), day = 20_000, merchant = "TESCO DUBLIN")
        r.categorise("t1", Category.DINING)
        r.record("t2", euro(-6820), day = 20_001, merchant = "TESCO CORK")
        r.categorise("t2", Category.GROCERIES)

        assertEquals(1, r.categoryRules().size)
        assertEquals(Category.GROCERIES, r.categoryRules()["tesco"])
    }

    @Test
    fun `forgetting a rule falls back to the seeds`() {
        val r = repo()
        r.record("t1", euro(-6820), day = 20_000, merchant = "TESCO")
        r.categorise("t1", Category.DINING)
        r.forgetCategoryRule("tesco")

        assertTrue(r.categoryRules().isEmpty())
        r.record("t2", euro(-1200), day = 20_001, merchant = "TESCO")
        val next = r.transactions().first { it.id == "t2" }
        assertEquals(Category.GROCERIES, next.categoryOrNull)
        assertEquals(CategorySource.SEED, next.categorySource)
    }

    @Test
    fun `a rule taught in one currency applies in another`() {
        // Rules match on text, so nothing about them refers to a currency.
        val r = repo()
        r.record("t1", euro(-2000), day = 20_000, merchant = "UBER BV AMSTERDAM")
        r.categorise("t1", Category.TRAVEL)
        r.record("t2", Money(-45_000, Currency.INR), day = 20_001, merchant = "UBER INDIA")
        assertEquals(Category.TRAVEL, r.transactions().first { it.id == "t2" }.categoryOrNull)
    }

    @Test
    fun `a suggestion can be asked for without recording anything`() {
        val r = repo()
        val suggestion = r.suggestCategory("SWIGGY BANGALORE")
        assertEquals(Category.DINING, suggestion?.category)
        assertEquals(CategorySource.SEED, suggestion?.source)
        assertTrue(r.transactions().isEmpty())
    }

    @Test
    fun `past entries can be restated but hand choices are left alone`() {
        val r = repo()
        r.record("guessed", euro(-6820), day = 20_000, merchant = "TESCO DUBLIN")
        r.record("byhand", euro(-1000), day = 20_001, merchant = "TESCO CORK")
        r.categorise("byhand", Category.HEALTH)
        r.record("subject", euro(-2000), day = 20_002, merchant = "TESCO NAAS")

        // Only the guessed one is a candidate; the hand-picked one is not.
        assertEquals(listOf("guessed"), r.pastMatching("subject").map { it.id })

        r.categorise("subject", Category.DINING, applyToPast = true)
        val byId = r.transactions().associateBy { it.id }
        assertEquals(Category.DINING, byId.getValue("guessed").categoryOrNull)
        assertEquals(CategorySource.LEARNED, byId.getValue("guessed").categorySource)
        // Untouched.
        assertEquals(Category.HEALTH, byId.getValue("byhand").categoryOrNull)
        assertEquals(CategorySource.MANUAL, byId.getValue("byhand").categorySource)
    }

    @Test
    fun `past entries are left as they were unless asked for`() {
        val r = repo()
        r.record("old", euro(-6820), day = 20_000, merchant = "TESCO DUBLIN")
        r.record("new", euro(-2000), day = 20_001, merchant = "TESCO NAAS")
        r.categorise("new", Category.DINING)
        assertEquals(Category.GROCERIES, r.transactions().first { it.id == "old" }.categoryOrNull)
    }

    @Test
    fun `another merchant's entries are never candidates`() {
        val r = repo()
        r.record("lidl", euro(-1000), day = 20_000, merchant = "LIDL CORK")
        r.record("tesco", euro(-2000), day = 20_001, merchant = "TESCO NAAS")
        assertTrue(r.pastMatching("tesco").isEmpty())
    }

    @Test
    fun `the merchant reads as a name while the raw string is kept`() {
        // The sheet keeps what the bank sent; the screen shows what a person
        // recognises.
        val r = repo()
        r.record("t1", euro(-6820), day = 20_000, merchant = "TESCO STORES 3421 DUBLIN IE")
        val t = r.transactions().single()
        assertEquals("TESCO STORES 3421 DUBLIN IE", t.merchant)
        assertEquals("Tesco", t.merchantLabel)
    }

    @Test
    fun `an unreadable merchant falls back to the raw string`() {
        // A blank row is worse than a noisy one.
        val r = repo()
        r.record("t1", euro(-100), day = 20_000, merchant = "POS 4419 XXXX IE")
        assertEquals("POS 4419 XXXX IE", r.transactions().single().merchantLabel)
    }

    @Test
    fun `a split is categorised on your share — not what you fronted`() {
        // PLAN.md §6 states this explicitly; the budget must not see the total.
        val r = repo()
        r.record(
            "t1", euro(-2000), day = 20_000, merchant = "TESCO",
            totalPaid = euro(-6000), splitWith = setOf("bob@example.com"),
        )
        val t = r.transactions().single()
        assertEquals(Category.GROCERIES, t.categoryOrNull)
        assertEquals(euro(-2000), t.share)
        assertEquals(2000L, r.ledgers().single().spent.minor)
    }
}
