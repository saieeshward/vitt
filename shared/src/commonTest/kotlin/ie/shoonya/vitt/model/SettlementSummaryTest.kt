package ie.shoonya.vitt.model

import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.sync.EventStore
import ie.shoonya.vitt.sync.testDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettlementSummaryTest {

    private val node = "a219e7a71cc18912"

    private fun repo(): LedgerRepository {
        var t = 1_000L
        return LedgerRepository(EventStore.open(testDriver(), node) { t++ }) { t }
    }

    private fun summaryFor(r: LedgerRepository, who: String, sender: String? = null) =
        SettlementSummary.forParticipant(
            participant = who,
            splits = r.transactions(),
            senderName = sender,
            formatDay = { "2026-08-31" },
        )

    @Test
    fun `a summary lists each split and totals it`() {
        val r = repo()
        r.record(
            "t1", Money(-2000, Currency.EUR), day = 20_000, merchant = "Pizzeria",
            totalPaid = Money(-6000, Currency.EUR), splitWith = setOf("bob@example.com"),
        )
        val m = assertNotNull(summaryFor(r, "bob@example.com"))
        assertTrue(m.body.contains("Pizzeria"))
        assertTrue(m.body.contains("EUR 60.00 paid"))
        assertTrue(m.body.contains("EUR 40.00 owed"))
        assertTrue(m.body.contains("Total owed: EUR 40.00"))
        assertEquals("Settling up — EUR 40.00", m.subject)
    }

    @Test
    fun `two currencies are listed rather than summed`() {
        val r = repo()
        r.record(
            "t1", Money(-2000, Currency.EUR), day = 20_000, merchant = "Pizzeria",
            totalPaid = Money(-6000, Currency.EUR), splitWith = setOf("bob@example.com"),
        )
        r.record(
            "t2", Money(-90_000, Currency.INR), day = 20_001, merchant = "Auto",
            totalPaid = Money(-180_000, Currency.INR), splitWith = setOf("bob@example.com"),
        )
        val m = assertNotNull(summaryFor(r, "bob@example.com"))
        assertTrue(m.body.contains("Total owed, per currency:"))
        assertTrue(m.body.contains("EUR 40.00"))
        assertTrue(m.body.contains("INR 900.00"))
        assertEquals("Settling up — 2 currencies", m.subject)
        // No blended figure anywhere — that would need a market rate.
        assertTrue(!m.body.contains("Total owed:"))
    }

    @Test
    fun `amounts are machine-plain so the recipient's locale cannot misread them`() {
        // PLAN.md:460 — `1.234` is 1234 in Germany and 1.234 in Ireland. The
        // recipient's locale is unknowable, so grouping is never applied.
        val r = repo()
        r.record(
            "t1", Money(-100_000, Currency.EUR), day = 20_000, merchant = "Flights",
            totalPaid = Money(-246_800, Currency.EUR), splitWith = setOf("bob@example.com"),
        )
        val m = assertNotNull(summaryFor(r, "bob@example.com"))
        assertTrue(m.body.contains("EUR 1468.00"))
        assertTrue(!m.body.contains("1,468"))
        assertTrue(!m.body.contains("1.468,00"))
    }

    @Test
    fun `a summary bills one person only their share of a shared split`() {
        val r = repo()
        r.record(
            "t1", Money(-3000, Currency.EUR), day = 20_000, merchant = "Taxi",
            totalPaid = Money(-9000, Currency.EUR),
            splitWith = setOf("bob@example.com", "cara@example.com"),
        )
        val m = assertNotNull(summaryFor(r, "bob@example.com"))
        assertTrue(m.body.contains("between 2"))
        assertTrue(m.body.contains("EUR 30.00 owed"))
        assertTrue(m.body.contains("Total owed: EUR 30.00"))
        // Never the whole remainder, which would bill the same money twice.
        assertTrue(!m.body.contains("EUR 60.00 owed"))
    }

    @Test
    fun `a settled split produces no summary`() {
        val r = repo()
        r.record(
            "t1", Money(-2000, Currency.EUR), day = 20_000,
            totalPaid = Money(-6000, Currency.EUR), splitWith = setOf("bob@example.com"),
        )
        r.settleInFull("t1")
        // Nothing outstanding, so there is no way to send an empty demand.
        assertNull(summaryFor(r, "bob@example.com"))
    }

    @Test
    fun `someone with no splits produces no summary`() {
        val r = repo()
        r.record(
            "t1", Money(-2000, Currency.EUR), day = 20_000,
            totalPaid = Money(-6000, Currency.EUR), splitWith = setOf("bob@example.com"),
        )
        assertNull(summaryFor(r, "cara@example.com"))
    }

    @Test
    fun `a partial repayment shows only what is left`() {
        val r = repo()
        r.record(
            "t1", Money(-2000, Currency.EUR), day = 20_000, merchant = "Pizzeria",
            totalPaid = Money(-6000, Currency.EUR), splitWith = setOf("bob@example.com"),
        )
        r.settle("t1", Money(1500, Currency.EUR))
        val m = assertNotNull(summaryFor(r, "bob@example.com"))
        assertTrue(m.body.contains("EUR 25.00 owed"))
        assertTrue(m.body.contains("Total owed: EUR 25.00"))
    }

    @Test
    fun `only the named person's splits appear`() {
        val r = repo()
        r.record(
            "t1", Money(-2000, Currency.EUR), day = 20_000, merchant = "Pizzeria",
            totalPaid = Money(-6000, Currency.EUR), splitWith = setOf("bob@example.com"),
        )
        r.record(
            "t2", Money(-1000, Currency.EUR), day = 20_001, merchant = "Taxi",
            totalPaid = Money(-3000, Currency.EUR), splitWith = setOf("cara@example.com"),
        )
        val m = assertNotNull(summaryFor(r, "bob@example.com"))
        assertTrue(m.body.contains("Pizzeria"))
        assertTrue(!m.body.contains("Taxi"))
    }

    @Test
    fun `a participant is matched case-insensitively`() {
        val r = repo()
        r.record(
            "t1", Money(-2000, Currency.EUR), day = 20_000,
            totalPaid = Money(-6000, Currency.EUR), splitWith = setOf("bob@example.com"),
        )
        assertNotNull(summaryFor(r, "Bob@Example.com"))
    }

    @Test
    fun `a sender name is signed off when given`() {
        val r = repo()
        r.record(
            "t1", Money(-2000, Currency.EUR), day = 20_000,
            totalPaid = Money(-6000, Currency.EUR), splitWith = setOf("bob@example.com"),
        )
        assertTrue(assertNotNull(summaryFor(r, "bob@example.com", sender = "Sai")).body.endsWith("— Sai"))
        assertTrue(!assertNotNull(summaryFor(r, "bob@example.com")).body.contains("—\n"))
    }

    @Test
    fun `a split with no merchant falls back to its category`() {
        val r = repo()
        r.record(
            "t1", Money(-2000, Currency.EUR), day = 20_000, category = "Groceries",
            totalPaid = Money(-6000, Currency.EUR), splitWith = setOf("bob@example.com"),
        )
        assertTrue(assertNotNull(summaryFor(r, "bob@example.com")).body.contains("Groceries"))
    }

    @Test
    fun `a split with neither merchant nor category still reads`() {
        val r = repo()
        r.record(
            "t1", Money(-2000, Currency.EUR), day = 20_000,
            totalPaid = Money(-6000, Currency.EUR), splitWith = setOf("bob@example.com"),
        )
        assertTrue(assertNotNull(summaryFor(r, "bob@example.com")).body.contains("expense"))
    }

    @Test
    fun `splits are listed oldest first`() {
        val r = repo()
        r.record(
            "t1", Money(-2000, Currency.EUR), day = 20_005, merchant = "Later",
            totalPaid = Money(-6000, Currency.EUR), splitWith = setOf("bob@example.com"),
        )
        r.record(
            "t2", Money(-1000, Currency.EUR), day = 20_000, merchant = "Earlier",
            totalPaid = Money(-3000, Currency.EUR), splitWith = setOf("bob@example.com"),
        )
        val body = assertNotNull(summaryFor(r, "bob@example.com")).body
        assertTrue(body.indexOf("Earlier") < body.indexOf("Later"))
    }
}
