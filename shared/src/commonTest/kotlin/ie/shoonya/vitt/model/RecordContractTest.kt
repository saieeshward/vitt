package ie.shoonya.vitt.model

import ie.shoonya.vitt.money.AmountEntry
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.sync.EventStore
import ie.shoonya.vitt.sync.testDriver
import ie.shoonya.vitt.time.Civil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * What the Add sheet can produce, against what [LedgerRepository.record] will
 * accept.
 *
 * `record` refuses bad input by throwing, which is right for a function at the
 * edge of an append-only log: a zero row or a cross-currency split can never be
 * taken back once written. The cost is that every one of those `require`s is a
 * live crash in a click handler, and a transaction the person watched
 * themselves save.
 *
 * So the contract is pinned from both ends. These tests say which inputs are
 * refused and, more importantly, that the shapes the keypad and the account
 * chips can actually produce are all accepted — because a refusal the UI can
 * reach is a bug in one of the two, and this is where it shows up rather than
 * on somebody's phone.
 */
class RecordContractTest {

    private fun repo(): LedgerRepository {
        var t = 1_000L
        return LedgerRepository(EventStore.open(testDriver(), "a219e7a71cc18912") { t++ }) { t }
    }

    private val day = Civil.toDays(2026, 9, 23)
    private fun eur(minor: Long) = Money(minor, Currency.EUR)

    // ---- what the UI can produce, all of which must be accepted -------------

    @Test
    fun `the plainest entry there is`() {
        val r = repo()
        r.record("a", eur(-1250), day = day)
        assertEquals(1, r.transactions().size)
    }

    @Test
    fun `no account is allowed — the chips are a toggle`() {
        // Tapping the chosen account clears it, which is deliberate: capture
        // often cannot say which account paid. It must not also be a refusal.
        val r = repo()
        r.record("a", eur(-1250), day = day, accountId = null)
        assertEquals(null, r.transactions().single().accountId)
    }

    @Test
    fun `no category is allowed — everything else is optional`() {
        val r = repo()
        r.record("a", eur(-1250), day = day, category = null)
        assertEquals(1, r.transactions().size)
    }

    @Test
    fun `an account the device has not seen yet is not a refusal`() {
        // Its opening event may still be in flight from another device, and
        // refusing would lose the transaction outright.
        val r = repo()
        r.record("a", eur(-1250), day = day, accountId = "from-another-phone")
        assertEquals(1, r.transactions().size)
    }

    @Test
    fun `income is the same call with the sign the other way`() {
        val r = repo()
        r.record("a", eur(318000), day = day, category = "income")
        assertTrue(r.transactions().single().amount.isInflow)
    }

    @Test
    fun `a split with participants and a total is accepted`() {
        val r = repo()
        r.record(
            "a", eur(-2000), day = day,
            totalPaid = eur(-6000), splitWith = setOf("bea", "ali"),
        )
        assertTrue(r.transactions().single().isSplit)
    }

    @Test
    fun `a split with nobody named yet is accepted`() {
        // The sheet saves first and asks who was in on it afterwards, so this
        // is the ordinary path rather than an edge.
        val r = repo()
        r.record("a", eur(-2000), day = day, totalPaid = eur(-6000))
        assertTrue(r.transactions().single().isSplit)
    }

    @Test
    fun `the largest amount the keypad can type is recordable`() {
        // Twelve whole digits and two of fraction. If Long could not hold the
        // minor units the keypad would be able to type an amount that cannot
        // be saved, and the first anybody would know is a crash on Save.
        val typed = "9".repeat(AmountEntry.MAX_WHOLE_DIGITS) + ".99"
        val entry = typed.fold(AmountEntry(currency = Currency.EUR)) { e, c -> e.press(c) }
        val r = repo()
        r.record("a", Money(-entry.money.minor, Currency.EUR), day = day)
        assertEquals(entry.money.minor, -r.transactions().single().amount.minor)
    }

    @Test
    fun `a currency without decimals is recordable`() {
        val r = repo()
        r.record("a", Money(-1200, Currency.JPY), day = day)
        assertEquals(Currency.JPY, r.transactions().single().amount.currency)
    }

    @Test
    fun `a note at the full length is recordable`() {
        val r = repo()
        r.record("a", eur(-1250), day = day, note = "x".repeat(Transaction.MAX_NOTE))
        assertEquals(Transaction.MAX_NOTE, r.transactions().single().note?.length)
    }

    // ---- what is refused, and must therefore be unreachable from the UI -----

    @Test
    fun `a zero is refused`() {
        // Which is why every Save gate checks hasValue rather than isEmpty:
        // "0" and "0." are typed on the way to "0.50".
        val r = repo()
        assertFailsWith<IllegalArgumentException> { r.record("a", eur(0), day = day) }
    }

    @Test
    fun `the keypad cannot produce a zero once it has a value`() {
        // The other end of the same contract. hasValue is false for every text
        // that folds to zero, so the Save button is not there to be pressed.
        listOf("", "0", "0.", "0.0", "0.00").forEach { typed ->
            val entry = typed.fold(AmountEntry(currency = Currency.EUR)) { e, c -> e.press(c) }
            assertTrue(!entry.hasValue, "\"$typed\" should not be saveable")
        }
    }

    @Test
    fun `a split across two currencies is refused`() {
        val r = repo()
        assertFailsWith<IllegalArgumentException> {
            r.record("a", eur(-2000), day = day, totalPaid = Money(-6000, Currency.INR))
        }
    }

    @Test
    fun `naming who it was split with without a total is refused`() {
        val r = repo()
        assertFailsWith<IllegalArgumentException> {
            r.record("a", eur(-2000), day = day, splitWith = setOf("bea"))
        }
    }

    @Test
    fun `a transaction into an account of another currency is refused`() {
        // The one refusal the UI could plausibly reach, if the account chips
        // ever stopped being filtered by the chosen currency.
        val r = repo()
        r.openAccount("a1", "AIB", Currency.EUR)
        assertFailsWith<IllegalArgumentException> {
            r.record("t", Money(-1250, Currency.INR), day = day, accountId = "a1")
        }
    }

    @Test
    fun `a refused save writes nothing at all`() {
        // The property the sheet depends on to keep the amount on screen and
        // let the person try again: a refusal is not a half-written row.
        val r = repo()
        assertFailsWith<IllegalArgumentException> { r.record("a", eur(0), day = day) }
        assertTrue(r.transactions().isEmpty())
    }

    @Test
    fun `a refusal leaves the next save working`() {
        val r = repo()
        assertFailsWith<IllegalArgumentException> { r.record("a", eur(0), day = day) }
        r.record("b", eur(-1250), day = day)
        assertEquals(1, r.transactions().size)
    }
}
