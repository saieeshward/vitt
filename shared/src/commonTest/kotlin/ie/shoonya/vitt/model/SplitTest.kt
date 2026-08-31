package ie.shoonya.vitt.model

import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.sync.EventStore
import ie.shoonya.vitt.sync.testDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SplitTest {

    private val node = "a219e7a71cc18912"

    private fun repo(): LedgerRepository {
        var t = 1_000L
        return LedgerRepository(EventStore.open(testDriver(), node) { t++ }) { t }
    }

    /** €60 dinner, your share €20 — so €40 is owed to you. */
    private fun LedgerRepository.dinner(vararg withWhom: String) = record(
        "t1",
        Money(-2000, Currency.EUR),
        day = 20_000,
        merchant = "Pizzeria",
        totalPaid = Money(-6000, Currency.EUR),
        splitWith = withWhom.toSet(),
    )

    @Test
    fun `a split records who was in on it`() {
        val r = repo()
        r.dinner("bob@example.com", "cara@example.com")
        assertEquals(
            setOf("bob@example.com", "cara@example.com"),
            r.transactions().single().splitWith,
        )
    }

    @Test
    fun `participants are matched case-insensitively`() {
        // One person arriving as Bob@ from a phone and bob@ from a laptop must
        // not fork into two.
        val r = repo()
        r.dinner("Bob@Example.COM")
        assertEquals(setOf("bob@example.com"), r.transactions().single().splitWith)
    }

    @Test
    fun `participant labels are trimmed`() {
        val r = repo()
        r.dinner("  bob@example.com  ")
        assertEquals(setOf("bob@example.com"), r.transactions().single().splitWith)
    }

    @Test
    fun `two concurrent adds of different people both survive`() {
        // The reason each participant is its own field. A single field holding a
        // JSON array would keep only the later write, silently losing one person.
        val r = repo()
        r.dinner()
        r.addSplitParticipant("t1", "bob@example.com")
        r.addSplitParticipant("t1", "cara@example.com")
        assertEquals(
            setOf("bob@example.com", "cara@example.com"),
            r.transactions().single().splitWith,
        )
    }

    @Test
    fun `removing a participant keeps the others`() {
        val r = repo()
        r.dinner("bob@example.com", "cara@example.com")
        r.removeSplitParticipant("t1", "bob@example.com")
        assertEquals(setOf("cara@example.com"), r.transactions().single().splitWith)
    }

    @Test
    fun `a re-add after a remove wins by being later`() {
        val r = repo()
        r.dinner()
        r.addSplitParticipant("t1", "bob@example.com")
        r.removeSplitParticipant("t1", "bob@example.com")
        r.addSplitParticipant("t1", "bob@example.com")
        assertEquals(setOf("bob@example.com"), r.transactions().single().splitWith)
    }

    @Test
    fun `a blank participant is refused`() {
        val r = repo()
        r.dinner()
        assertFailsWith<IllegalArgumentException> { r.addSplitParticipant("t1", "   ") }
    }

    @Test
    fun `naming participants without a total paid is refused`() {
        // Without the full amount there is no share to owe, so the participant
        // list would be a claim about nothing.
        val r = repo()
        assertFailsWith<IllegalArgumentException> {
            r.record("t1", Money(-2000, Currency.EUR), day = 20_000, splitWith = setOf("bob@x.com"))
        }
    }

    @Test
    fun `a fresh split owes the whole difference`() {
        val r = repo()
        r.dinner("bob@example.com")
        val t = r.transactions().single()
        assertEquals(Money(4000, Currency.EUR), t.owed())
        assertEquals(Money(4000, Currency.EUR), t.outstanding())
        assertFalse(t.isSettled)
    }

    @Test
    fun `a partial repayment reduces what is outstanding`() {
        val r = repo()
        r.dinner("bob@example.com")
        r.settle("t1", Money(1500, Currency.EUR))
        val t = r.transactions().single()
        assertEquals(Money(4000, Currency.EUR), t.owed())
        assertEquals(Money(2500, Currency.EUR), t.outstanding())
        assertFalse(t.isSettled)
    }

    @Test
    fun `a full repayment settles the split`() {
        val r = repo()
        r.dinner("bob@example.com")
        r.settleInFull("t1")
        val t = r.transactions().single()
        assertTrue(t.isSettled)
        assertEquals(0L, t.outstanding()?.minor)
        assertTrue(r.openSplits().isEmpty())
    }

    @Test
    fun `an over-repayment floors at zero rather than going negative`() {
        // Someone rounding up does not mean the user now owes them money, and a
        // negative would quietly net against another split.
        val r = repo()
        r.dinner("bob@example.com")
        r.settle("t1", Money(5000, Currency.EUR))
        assertEquals(0L, r.transactions().single().outstanding()?.minor)
        assertTrue(r.owed().isEmpty())
    }

    @Test
    fun `settling is absolute — not incremental`() {
        // PLAN.md:220 — never a counter CRDT: two devices correcting the same
        // repayment to the same figure must not sum to double.
        val r = repo()
        r.dinner("bob@example.com")
        r.settle("t1", Money(1000, Currency.EUR))
        r.settle("t1", Money(1000, Currency.EUR))
        assertEquals(Money(3000, Currency.EUR), r.transactions().single().outstanding())
    }

    @Test
    fun `a negative repayment is refused`() {
        val r = repo()
        r.dinner("bob@example.com")
        assertFailsWith<IllegalArgumentException> { r.settle("t1", Money(-100, Currency.EUR)) }
    }

    @Test
    fun `an unsplit transaction owes nothing`() {
        val r = repo()
        r.record("t1", Money(-2000, Currency.EUR), day = 20_000)
        val t = r.transactions().single()
        assertNull(t.owed())
        assertNull(t.outstanding())
        assertFalse(t.isSettled)
    }

    @Test
    fun `the owed rollup nets off repayments`() {
        val r = repo()
        r.dinner("bob@example.com")
        assertEquals(Money(4000, Currency.EUR), r.owed()[Currency.EUR])
        r.settle("t1", Money(4000, Currency.EUR))
        assertTrue(r.owed().isEmpty())
    }

    @Test
    fun `what one person owes is reported per currency — never blended`() {
        val r = repo()
        r.record(
            "t1", Money(-2000, Currency.EUR), day = 20_000, merchant = "Pizzeria",
            totalPaid = Money(-6000, Currency.EUR), splitWith = setOf("bob@example.com"),
        )
        r.record(
            "t2", Money(-90_000, Currency.INR), day = 20_001, merchant = "Auto",
            totalPaid = Money(-180_000, Currency.INR), splitWith = setOf("bob@example.com"),
        )

        val owed = r.outstandingBy("bob@example.com")
        assertEquals(Money(4000, Currency.EUR), owed[Currency.EUR])
        assertEquals(Money(90_000, Currency.INR), owed[Currency.INR])
        assertEquals(2, owed.size)
    }

    @Test
    fun `what one person owes excludes other people's splits`() {
        val r = repo()
        r.record(
            "t1", Money(-2000, Currency.EUR), day = 20_000,
            totalPaid = Money(-6000, Currency.EUR), splitWith = setOf("bob@example.com"),
        )
        r.record(
            "t2", Money(-1000, Currency.EUR), day = 20_001,
            totalPaid = Money(-3000, Currency.EUR), splitWith = setOf("cara@example.com"),
        )
        assertEquals(Money(4000, Currency.EUR), r.outstandingBy("bob@example.com")[Currency.EUR])
        assertEquals(Money(2000, Currency.EUR), r.outstandingBy("cara@example.com")[Currency.EUR])
    }

    @Test
    fun `a debt shared between two people is not billed to each in full`() {
        // Caught in the simulator: a EUR 90 taxi split three ways showed EUR 40
        // outstanding under *both* named participants, so one debt read as two.
        val r = repo()
        r.record(
            "t1", Money(-3000, Currency.EUR), day = 20_000, merchant = "Taxi",
            totalPaid = Money(-9000, Currency.EUR),
            splitWith = setOf("bob@example.com", "cara@example.com"),
        )
        assertEquals(Money(3000, Currency.EUR), r.outstandingBy("bob@example.com")[Currency.EUR])
        assertEquals(Money(3000, Currency.EUR), r.outstandingBy("cara@example.com")[Currency.EUR])
        // And together they account for exactly what is owed, no more.
        assertEquals(Money(6000, Currency.EUR), r.transactions().single().outstanding())
    }

    @Test
    fun `an odd share distributes the remainder without inventing money`() {
        val r = repo()
        r.record(
            "t1", Money(-1, Currency.EUR), day = 20_000,
            totalPaid = Money(-101, Currency.EUR),
            splitWith = setOf("a@x.com", "b@x.com", "c@x.com"),
        )
        val t = r.transactions().single()
        val shares = listOf("a@x.com", "b@x.com", "c@x.com").map { t.outstandingFor(it)!!.minor }
        assertEquals(100L, t.outstanding()!!.minor)
        assertEquals(100L, shares.sum())
        assertEquals(listOf(34L, 33L, 33L), shares)
    }

    @Test
    fun `a partial repayment reduces every participant's share`() {
        val r = repo()
        r.record(
            "t1", Money(-3000, Currency.EUR), day = 20_000,
            totalPaid = Money(-9000, Currency.EUR),
            splitWith = setOf("bob@example.com", "cara@example.com"),
        )
        r.settle("t1", Money(2000, Currency.EUR))
        assertEquals(Money(2000, Currency.EUR), r.outstandingBy("bob@example.com")[Currency.EUR])
        assertEquals(Money(2000, Currency.EUR), r.outstandingBy("cara@example.com")[Currency.EUR])
    }

    @Test
    fun `somebody not in a split owes nothing on it`() {
        val r = repo()
        r.dinner("bob@example.com")
        assertNull(r.transactions().single().outstandingFor("cara@example.com"))
    }

    @Test
    fun `open participants are listed once and sorted`() {
        val r = repo()
        r.record(
            "t1", Money(-2000, Currency.EUR), day = 20_000,
            totalPaid = Money(-6000, Currency.EUR),
            splitWith = setOf("cara@example.com", "bob@example.com"),
        )
        r.record(
            "t2", Money(-1000, Currency.EUR), day = 20_001,
            totalPaid = Money(-2000, Currency.EUR), splitWith = setOf("bob@example.com"),
        )
        assertEquals(listOf("bob@example.com", "cara@example.com"), r.openSplitParticipants())
    }

    @Test
    fun `a settled split leaves the open list`() {
        val r = repo()
        r.dinner("bob@example.com")
        r.settleInFull("t1")
        assertTrue(r.openSplitParticipants().isEmpty())
        assertTrue(r.outstandingBy("bob@example.com").isEmpty())
    }

    @Test
    fun `categorisation still applies to the user's share — not the total`() {
        // PLAN.md:293 — the split must not inflate a category by what was fronted.
        val r = repo()
        r.dinner("bob@example.com")
        val t = r.transactions().single()
        assertEquals(Money(-2000, Currency.EUR), t.share)
        assertEquals(2000L, r.ledgers().single().spent.minor)
    }
}
