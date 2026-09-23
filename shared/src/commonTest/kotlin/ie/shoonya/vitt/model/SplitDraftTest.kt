package ie.shoonya.vitt.model

import ie.shoonya.vitt.model.SplitDraft.Companion.YOU
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SplitDraftTest {

    private fun eur(minor: Long) = Money(minor, Currency.EUR)

    /** €60 for you, Bea and Cal. */
    private val dinner = SplitDraft.of(eur(6000), listOf("Cal", "bea"))

    @Test
    fun `a new split is equal over everybody — you included`() {
        assertEquals(mapOf(YOU to eur(2000), "bea" to eur(2000), "cal" to eur(2000)), dinner.amounts)
        assertTrue(dinner.isEqual)
        assertTrue(dinner.isValid)
        assertNull(dinner.othersToSave)
    }

    @Test
    fun `typing one amount leaves it put and the untouched rows share the rest`() {
        // Bea had the steak: €30. You and Cal share the other €30.
        val d = dinner.set("bea", eur(3000))
        assertEquals(mapOf(YOU to eur(1500), "bea" to eur(3000), "cal" to eur(1500)), d.amounts)
        assertTrue(d.isValid)
        assertEquals(mapOf("bea" to eur(3000), "cal" to eur(1500)), d.othersToSave)
    }

    @Test
    fun `typing a second amount never moves the first`() {
        val d = dinner.set("bea", eur(3000)).set("cal", eur(1000))
        assertEquals(eur(3000), d.amounts["bea"])
        assertEquals(eur(2000), d.yours)
    }

    @Test
    fun `typed amounts past the bill say by how much`() {
        val d = dinner.set("bea", eur(4000)).set("cal", eur(3000))
        assertEquals(eur(1000), d.over)
        assertFalse(d.isValid)
    }

    @Test
    fun `every row typed and short of the bill says what is left`() {
        val d = dinner.set(YOU, eur(1000)).set("bea", eur(1000)).set("cal", eur(1000))
        assertEquals(eur(3000), d.unassigned)
        assertFalse(d.isValid)
    }

    @Test
    fun `the others taking the whole bill is a bill paid for them — and valid`() {
        // A ticket bought for a friend: your part is nothing, theirs is all.
        val d = dinner.set("bea", eur(3000)).set("cal", eur(3000))
        assertEquals(eur(0), d.yours)
        assertTrue(d.isValid)
    }

    @Test
    fun `the odd cent is yours — not a friend's`() {
        val d = SplitDraft.of(eur(10000), listOf("bea", "cal"))
        assertEquals(eur(3334), d.yours)
        assertEquals(eur(3333), d.amounts["bea"])
    }

    @Test
    fun `somebody in re-splits equally and somebody out takes their typed amount with them`() {
        val d = dinner.set("bea", eur(3000)).toggle("dev")
        assertEquals(listOf("bea", "cal", "dev"), d.people)
        assertEquals(eur(1000), d.amounts["dev"])
        val out = d.toggle("bea")
        assertFalse("bea" in out.fixed)
        assertEquals(eur(2000), out.yours)
    }

    @Test
    fun `equal again clears every typed amount`() {
        assertEquals(dinner, dinner.set("bea", eur(3000)).equal())
    }

    @Test
    fun `a new bill keeps typed amounts where they were`() {
        val d = dinner.set("bea", eur(3000)).withTotal(eur(9000))
        assertEquals(eur(3000), d.amounts["bea"])
        assertEquals(eur(3000), d.yours)
    }

    @Test
    fun `saving equal and reading it back gives the same cents`() {
        // Equal is not stored, so the stored reading must land on exactly the
        // figures the draft showed.
        val d = SplitDraft.of(eur(10000), listOf("bea", "cal"))
        val t = txn(bill = eur(10000), yours = d.yours, others = d.othersToSave)
        assertEquals(d.others, t.shares())
    }

    @Test
    fun `a saved split reads back as it was`() {
        val custom = txn(yours = eur(1500), others = mapOf("bea" to eur(3000), "cal" to eur(1500)))
        assertEquals(
            mapOf(YOU to eur(1500), "bea" to eur(3000), "cal" to eur(1500)),
            SplitDraft.of(custom).amounts,
        )
        // An old split where only your own share was typed keeps it typed.
        val legacy = txn(yours = eur(1000), others = null)
        val d = SplitDraft.of(legacy)
        assertEquals(eur(1000), d.yours)
        assertEquals(eur(2500), d.amounts["bea"])
        assertEquals(eur(1000), d.fixed[YOU])
    }

    private fun txn(yours: Money, others: Map<String, Money>?, bill: Money = eur(6000)) = Transaction(
        id = "t1", amount = -yours, merchant = null, category = null, categorySource = null,
        accountId = null, day = 20_000, totalPaid = -bill,
        splitWith = setOf("bea", "cal"), settled = eur(0), note = null, deleted = false,
        customShares = others.orEmpty(),
    )
}
