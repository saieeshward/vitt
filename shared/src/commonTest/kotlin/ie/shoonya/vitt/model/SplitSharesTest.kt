package ie.shoonya.vitt.model

import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.sync.EventStore
import ie.shoonya.vitt.sync.testDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Per-person amounts on a split, and repayments recorded per person. */
class SplitSharesTest {

    private fun eur(minor: Long) = Money(minor, Currency.EUR)

    private fun repo(): LedgerRepository {
        var t = 1_000L
        return LedgerRepository(EventStore.open(testDriver(), "a219e7a71cc18912") { t++ }) { t }
    }

    /** €60 dinner three ways: yours €20, Bea and Cal €20 each. */
    private fun LedgerRepository.dinner(): Transaction {
        record("t1", eur(-2000), day = 20_000, merchant = "Pizzeria", totalPaid = eur(-6000), splitWith = setOf("bea", "cal"))
        return split()
    }

    private fun LedgerRepository.split() = transactions().single { it.id == "t1" }

    @Test
    fun `with nothing entered per person the split is equal`() {
        val t = repo().dinner()
        assertEquals(mapOf("bea" to eur(2000), "cal" to eur(2000)), t.shares())
        assertFalse(t.hasCustomShares)
    }

    @Test
    fun `an amount per person is kept — and your share is what is left`() {
        // Bea had the steak.
        val r = repo()
        r.dinner()
        r.editSplit("t1", total = eur(6000), yours = eur(1500), others = mapOf("bea" to eur(3000), "cal" to eur(1500)))

        val t = r.split()
        assertTrue(t.hasCustomShares)
        assertEquals(mapOf("bea" to eur(3000), "cal" to eur(1500)), t.shares())
        // The budget sees only your part, as before: that is the point.
        assertEquals(eur(-1500), t.amount)
        assertEquals(eur(-6000), t.totalPaid)
        assertEquals(eur(3000), t.outstandingFor("bea"))
    }

    @Test
    fun `shares that do not add up to the bill are refused`() {
        // An append-only log keeps a split whose parts exceed its whole for good.
        val r = repo()
        r.dinner()
        assertFailsWith<IllegalArgumentException> {
            r.editSplit("t1", total = eur(6000), yours = eur(1500), others = mapOf("bea" to eur(3000), "cal" to eur(2000)))
        }
    }

    @Test
    fun `your share cannot be more than the bill`() {
        val r = repo()
        r.dinner()
        assertFailsWith<IllegalArgumentException> { r.editSplit("t1", total = eur(6000), yours = eur(7000), others = null) }
    }

    @Test
    fun `a bill paid entirely for others is recorded and read back`() {
        // Your part is nothing, the whole bill is owed back, and it stays money
        // out: the bill carries the sign when your share has none.
        val r = repo()
        r.record(
            "gift", eur(0), day = 20_000, totalPaid = eur(-4000), splitWith = setOf("bea"),
            shares = mapOf("bea" to eur(4000)),
        )
        val t = r.transactions().single { it.id == "gift" }
        assertEquals(eur(4000), t.outstandingFor("bea"))
        assertFalse(t.isSpend, "nothing of it is your own spending")
        r.editSplit("gift", total = eur(5000), yours = eur(0), others = mapOf("bea" to eur(5000)))
        assertEquals(eur(-5000), r.transactions().single { it.id == "gift" }.totalPaid)
    }

    @Test
    fun `a zero with no bill is still refused`() {
        assertFailsWith<IllegalArgumentException> { repo().record("z", eur(0), day = 20_000) }
    }

    @Test
    fun `amounts must name exactly the people on the split`() {
        val r = repo()
        r.dinner()
        assertFailsWith<IllegalArgumentException> {
            r.editSplit("t1", total = eur(6000), yours = eur(2000), others = mapOf("bea" to eur(4000)))
        }
    }

    @Test
    fun `changing the bill with an equal split keeps it equal`() {
        val r = repo()
        r.dinner()
        r.editSplit("t1", total = eur(9000), yours = eur(3000), others = null)
        assertEquals(mapOf("bea" to eur(3000), "cal" to eur(3000)), r.split().shares())
    }

    @Test
    fun `adding somebody puts the split back to equal`() {
        // Amounts right for three people are wrong for four.
        val r = repo()
        r.dinner()
        r.editSplit("t1", total = eur(6000), yours = eur(1500), others = mapOf("bea" to eur(3000), "cal" to eur(1500)))
        r.addSplitParticipant("t1", "dev")

        val t = r.split()
        assertFalse(t.hasCustomShares)
        assertEquals(eur(4500), t.shares().values.fold(eur(0)) { a, m -> a + m })
        // And taking them out again does not quietly bring the old amounts back.
        r.removeSplitParticipant("t1", "dev")
        assertFalse(r.split().hasCustomShares)
    }

    @Test
    fun `stored amounts that no longer fit are read as equal rather than trusted`() {
        val t = repo().dinner().copy(customShares = mapOf("bea" to eur(3000)))
        assertFalse(t.hasCustomShares)
        assertEquals(eur(2000), t.shares()["cal"])
    }

    @Test
    fun `one person paying back clears only them`() {
        // The bug this replaced: one settled total, divided equally, showed
        // Bea and Cal each owing €10 after Bea had paid her €20 in full.
        val r = repo()
        r.dinner()
        r.settleParticipant("t1", "bea", eur(2000))

        val t = r.split()
        assertEquals(eur(0), t.outstandingFor("bea"))
        assertEquals(eur(2000), t.outstandingFor("cal"))
        assertEquals(eur(2000), t.outstanding())
        assertEquals(mapOf(Currency.EUR to eur(2000)), r.outstandingBy("cal"))
        assertTrue(r.outstandingBy("bea").isEmpty())
    }

    @Test
    fun `paying back more than a share floors at nothing — and cannot cover someone else`() {
        val r = repo()
        r.dinner()
        r.settleParticipant("t1", "bea", eur(5000))
        assertEquals(eur(0), r.split().outstandingFor("bea"))
        assertEquals(eur(2000), r.split().outstanding())
    }

    @Test
    fun `settled up records each person as paid`() {
        val r = repo()
        r.dinner()
        r.editSplit("t1", total = eur(6000), yours = eur(1500), others = mapOf("bea" to eur(3000), "cal" to eur(1500)))
        r.settleInFull("t1")
        val t = r.split()
        assertTrue(t.isSettled)
        assertEquals(mapOf("bea" to eur(3000), "cal" to eur(1500)), t.settledBy)
    }

    @Test
    fun `an old repayment total still reads the way it did`() {
        // Recorded before repayments were per person: spread equally, as it
        // always was.
        val r = repo()
        r.dinner()
        r.settle("t1", eur(2000))
        val t = r.split()
        assertEquals(eur(1000), t.outstandingFor("bea"))
        assertEquals(eur(1000), t.outstandingFor("cal"))
        assertEquals(eur(2000), t.totalSettled)
    }

    @Test
    fun `shares survive their own encoding — names with spaces included`() {
        val shares = mapOf("bea o'neill" to eur(1234), "cal@example.com" to eur(0))
        assertEquals(shares, Transaction.decodeShares(Transaction.encodeShares(shares), Currency.EUR))
        assertTrue(Transaction.decodeShares("", Currency.EUR).isEmpty())
        assertTrue(Transaction.decodeShares("junk\n-5\tbea", Currency.EUR).isEmpty())
    }

    @Test
    fun `the remainder cent goes to the same person on every device`() {
        // €10 between three: 334, 333, 333, always in the same order.
        val r = repo()
        r.record("t2", eur(-1000), day = 20_000, totalPaid = eur(-2000), splitWith = setOf("cal", "abe", "bea"))
        val shares = r.transactions().single { it.id == "t2" }.shares()
        assertEquals(listOf(334L, 333L, 333L), listOf("abe", "bea", "cal").map { shares.getValue(it).minor })
    }

    @Test
    fun `a settle-up summary leaves out what that person has already paid`() {
        val r = repo()
        r.dinner()
        r.settleParticipant("t1", "bea", eur(2000))
        val splits = r.transactions()
        assertEquals(null, SettlementSummary.forParticipant("bea", splits))
        assertTrue(SettlementSummary.forParticipant("cal", splits)!!.body.contains("20.00"))
    }

    @Test
    fun `a split is recorded with its people and amounts in one go`() {
        val r = repo()
        r.record(
            "t3", eur(-1500), day = 20_000, totalPaid = eur(-6000), splitWith = setOf("bea", "cal"),
            shares = mapOf("bea" to eur(3000), "cal" to eur(1500)),
        )
        val t = r.transactions().single { it.id == "t3" }
        assertTrue(t.hasCustomShares)
        assertEquals(eur(3000), t.shares()["bea"])
    }

    @Test
    fun `a split recorded with amounts that do not add up is refused`() {
        assertFailsWith<IllegalArgumentException> {
            repo().record(
                "t3", eur(-1500), day = 20_000, totalPaid = eur(-6000), splitWith = setOf("bea", "cal"),
                shares = mapOf("bea" to eur(3000), "cal" to eur(3000)),
            )
        }
    }

    @Test
    fun `editing can change who is on it and their amounts together`() {
        val r = repo()
        r.dinner()
        r.editSplit(
            "t1", total = eur(6000), yours = eur(2000),
            others = mapOf("bea" to eur(2500), "dev" to eur(1500)), people = setOf("bea", "dev"),
        )
        val t = r.split()
        assertEquals(setOf("bea", "dev"), t.splitWith)
        assertTrue(t.hasCustomShares)
        assertEquals(eur(1500), t.shares()["dev"])
    }

    @Test
    fun `people split with before come most recent first`() {
        val r = repo()
        r.record("a", eur(-1000), day = 20_000, totalPaid = eur(-2000), splitWith = setOf("old"))
        r.record("b", eur(-1000), day = 20_010, totalPaid = eur(-3000), splitWith = setOf("bea", "cal"))
        assertEquals(listOf("bea", "cal", "old"), r.knownPeople())
    }

    @Test
    fun `settling up with one person clears every split they were on — and nobody else's part`() {
        val r = repo()
        r.dinner()
        r.record("t2", eur(-1000), day = 20_001, merchant = "Taxi", totalPaid = eur(-2000), splitWith = setOf("bea"))
        r.settleParticipant("t1", "bea", eur(500))

        r.settleUpWith("Bea")

        assertEquals(emptyMap(), r.outstandingBy("bea"))
        assertEquals(eur(2000), r.split().settledBy["bea"])
        assertEquals(eur(2000), r.split().outstandingFor("cal"))
        assertTrue(r.transactions().single { it.id == "t2" }.isSettled)
        // Done, though the dinner stays open for Cal.
        assertEquals(listOf("cal"), r.openSplitParticipants())
    }
}
