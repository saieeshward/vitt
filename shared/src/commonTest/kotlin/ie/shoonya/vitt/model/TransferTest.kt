package ie.shoonya.vitt.model

import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.sync.Event
import ie.shoonya.vitt.sync.EventStore
import ie.shoonya.vitt.sync.TaggedValue
import ie.shoonya.vitt.sync.testDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransferTest {

    private val node = "a219e7a71cc18912"

    private class Harness(val store: EventStore, val now: () -> Long) {
        val repo = LedgerRepository(store, now)

        fun put(entity: String, id: String, field: String, value: TaggedValue) {
            store.append(Event(store.issue(), entity, id, field, value), now())
        }
    }

    private fun harness(): Harness {
        var t = 1_000L
        val store = EventStore.open(testDriver(), node) { t++ }
        return Harness(store) { t }
    }

    private fun repo(): LedgerRepository = harness().repo

    /** A EUR current account and an INR one, the app's core case. */
    private fun LedgerRepository.twoAccounts() {
        openAccount("eur", "AIB", Currency.EUR, opening = Money(100_000, Currency.EUR))
        openAccount("inr", "HDFC", Currency.INR, opening = Money(0, Currency.INR))
    }

    @Test
    fun `a same-currency transfer moves the balance and spends nothing`() {
        val r = repo()
        r.openAccount("cur", "Current", Currency.EUR, opening = Money(100_000, Currency.EUR))
        r.openAccount("sav", "Savings", Currency.EUR, AccountKind.SAVINGS)
        r.transfer("m1", "cur", "sav", Money(50_000, Currency.EUR), Money(50_000, Currency.EUR), day = 20_000)

        val byId = r.accountBalances().associateBy { it.account.id }
        assertEquals(50_000L, byId.getValue("cur").balance.minor)
        assertEquals(50_000L, byId.getValue("sav").balance.minor)

        // The whole reason transfers are their own entity: no phantom spending.
        assertTrue(r.transactions().isEmpty())
        assertTrue(r.ledgers().isEmpty())
    }

    @Test
    fun `a cross-currency transfer locks the rate it actually moved at`() {
        val r = repo()
        r.twoAccounts()
        r.transfer("m1", "eur", "inr", Money(50_000, Currency.EUR), Money(4_500_000, Currency.INR), day = 20_000)

        val t = r.transfers().single()
        assertTrue(t.isCrossCurrency)
        assertEquals("90.000000", t.rate?.toPlainString())
    }

    @Test
    fun `a cross-currency transfer moves both balances in their own currencies`() {
        val r = repo()
        r.twoAccounts()
        r.transfer("m1", "eur", "inr", Money(50_000, Currency.EUR), Money(4_500_000, Currency.INR), day = 20_000)

        val byId = r.accountBalances().associateBy { it.account.id }
        assertEquals(Money(50_000, Currency.EUR), byId.getValue("eur").balance)
        assertEquals(Money(4_500_000, Currency.INR), byId.getValue("inr").balance)
        assertEquals(1, byId.getValue("eur").transferCount)
        assertEquals(0, byId.getValue("eur").transactionCount)
    }

    @Test
    fun `a transfer never appears as spending in any ledger`() {
        val r = repo()
        r.twoAccounts()
        r.record("t1", Money(-1250, Currency.EUR), day = 20_000, accountId = "eur")
        r.transfer("m1", "eur", "inr", Money(50_000, Currency.EUR), Money(4_500_000, Currency.INR), day = 20_001)

        // Only the €12.50 is spending. The €500 moved, and moving your own money
        // is not spending it.
        val eur = r.ledgers().single { it.currency == Currency.EUR }
        assertEquals(1250L, eur.spent.minor)
        assertEquals(0L, eur.received.minor)
        // And the INR that arrived is not income.
        assertTrue(r.ledgers().none { it.currency == Currency.INR })
    }

    @Test
    fun `a same-currency transfer that lost money reports the fee`() {
        val r = repo()
        r.openAccount("a", "A", Currency.EUR, opening = Money(100_000, Currency.EUR))
        r.openAccount("b", "B", Currency.EUR)
        r.transfer("m1", "a", "b", Money(50_000, Currency.EUR), Money(49_750, Currency.EUR), day = 20_000)

        val t = r.transfers().single()
        assertEquals(Money(250, Currency.EUR), t.fee)
        assertNull(t.rate)
        // The €2.50 simply is not in either account any more.
        val byId = r.accountBalances().associateBy { it.account.id }
        assertEquals(50_000L, byId.getValue("a").balance.minor)
        assertEquals(49_750L, byId.getValue("b").balance.minor)
    }

    @Test
    fun `a cross-currency fee is null rather than invented`() {
        // Separating the fee from the spread would need a mid-market reference
        // rate, which is an outside estimate. §0.6 does not allow one.
        val r = repo()
        r.twoAccounts()
        r.transfer("m1", "eur", "inr", Money(50_000, Currency.EUR), Money(4_455_000, Currency.INR), day = 20_000)
        assertNull(r.transfers().single().fee)
    }

    @Test
    fun `observed rates are only pairs the user actually transferred`() {
        val r = repo()
        r.twoAccounts()
        r.openAccount("gbp", "Monzo", Currency.GBP)
        r.transfer("m1", "eur", "inr", Money(50_000, Currency.EUR), Money(4_500_000, Currency.INR), day = 20_000)
        r.transfer("m2", "eur", "inr", Money(10_000, Currency.EUR), Money(880_000, Currency.INR), day = 20_010)

        val rates = r.observedRates()
        assertEquals(2, rates.size)
        // Newest first, and each is its own fact — no interpolation between them.
        assertEquals(20_010, rates.first().first)
        assertEquals("88.000000", rates.first().second.toPlainString())
        assertEquals("90.000000", rates.last().second.toPlainString())
        // Nothing for EUR→GBP, which was never transferred.
        assertTrue(rates.none { it.second.to == Currency.GBP })
    }

    @Test
    fun `a same-currency transfer contributes no rate`() {
        val r = repo()
        r.openAccount("a", "A", Currency.EUR, opening = Money(100_000, Currency.EUR))
        r.openAccount("b", "B", Currency.EUR)
        r.transfer("m1", "a", "b", Money(1000, Currency.EUR), Money(1000, Currency.EUR), day = 20_000)
        assertTrue(r.observedRates().isEmpty())
    }

    @Test
    fun `a transfer to the same account is refused`() {
        val r = repo()
        r.twoAccounts()
        assertFailsWith<IllegalArgumentException> {
            r.transfer("m1", "eur", "eur", Money(1000, Currency.EUR), Money(1000, Currency.EUR), day = 20_000)
        }
    }

    @Test
    fun `a transfer needs two positive magnitudes`() {
        val r = repo()
        r.twoAccounts()
        // A signed amount would make a transfer that silently runs backwards
        // representable; direction belongs to the account fields alone.
        assertFailsWith<IllegalArgumentException> {
            r.transfer("m1", "eur", "inr", Money(-50_000, Currency.EUR), Money(4_500_000, Currency.INR), day = 20_000)
        }
        assertFailsWith<IllegalArgumentException> {
            r.transfer("m2", "eur", "inr", Money(50_000, Currency.EUR), Money(0, Currency.INR), day = 20_000)
        }
    }

    @Test
    fun `a leg in the wrong currency for its account is refused`() {
        val r = repo()
        r.twoAccounts()
        assertFailsWith<IllegalArgumentException> {
            r.transfer("m1", "eur", "inr", Money(50_000, Currency.INR), Money(4_500_000, Currency.INR), day = 20_000)
        }
        assertFailsWith<IllegalArgumentException> {
            r.transfer("m2", "eur", "inr", Money(50_000, Currency.EUR), Money(4_500_000, Currency.EUR), day = 20_000)
        }
    }

    @Test
    fun `a transfer involving an account this device has not seen is allowed`() {
        // The account event may still be in flight from another device.
        val r = repo()
        r.openAccount("eur", "AIB", Currency.EUR, opening = Money(100_000, Currency.EUR))
        r.transfer("m1", "eur", "not-yet-known", Money(50_000, Currency.EUR), Money(4_500_000, Currency.INR), day = 20_000)
        assertEquals(1, r.transfers().size)
        assertEquals(50_000L, r.accountBalances().single().balance.minor)
    }

    @Test
    fun `deleting a transfer restores both balances`() {
        val r = repo()
        r.twoAccounts()
        r.transfer("m1", "eur", "inr", Money(50_000, Currency.EUR), Money(4_500_000, Currency.INR), day = 20_000)
        r.deleteTransfer("m1")

        assertTrue(r.transfers().isEmpty())
        val byId = r.accountBalances().associateBy { it.account.id }
        assertEquals(100_000L, byId.getValue("eur").balance.minor)
        assertEquals(0L, byId.getValue("inr").balance.minor)
    }

    @Test
    fun `a hand-edited transfer whose accounts are the same is dropped`() {
        val h = harness()
        val r = h.repo
        r.twoAccounts()
        h.put(Transfer.ENTITY, "m1", Transfer.FIELD_FROM, TaggedValue.Str("eur"))
        h.put(Transfer.ENTITY, "m1", Transfer.FIELD_TO, TaggedValue.Str("eur"))
        h.put(Transfer.ENTITY, "m1", Transfer.FIELD_SENT, TaggedValue.Num(1000))
        h.put(Transfer.ENTITY, "m1", Transfer.FIELD_SENT_CURRENCY, TaggedValue.Str("EUR"))
        h.put(Transfer.ENTITY, "m1", Transfer.FIELD_RECEIVED, TaggedValue.Num(1000))
        h.put(Transfer.ENTITY, "m1", Transfer.FIELD_RECEIVED_CURRENCY, TaggedValue.Str("EUR"))

        assertTrue(r.transfers().isEmpty())
        assertEquals(100_000L, r.accountBalances().first { it.account.id == "eur" }.balance.minor)
    }

    @Test
    fun `a hand-edited transfer missing a leg is dropped`() {
        val h = harness()
        val r = h.repo
        r.twoAccounts()
        h.put(Transfer.ENTITY, "m1", Transfer.FIELD_FROM, TaggedValue.Str("eur"))
        h.put(Transfer.ENTITY, "m1", Transfer.FIELD_TO, TaggedValue.Str("inr"))
        h.put(Transfer.ENTITY, "m1", Transfer.FIELD_SENT, TaggedValue.Num(1000))
        h.put(Transfer.ENTITY, "m1", Transfer.FIELD_SENT_CURRENCY, TaggedValue.Str("EUR"))
        assertTrue(r.transfers().isEmpty())
    }

    @Test
    fun `a hand-edited transfer leg in the wrong currency does not corrupt a balance`() {
        val h = harness()
        val r = h.repo
        r.twoAccounts()
        h.put(Transfer.ENTITY, "m1", Transfer.FIELD_FROM, TaggedValue.Str("eur"))
        h.put(Transfer.ENTITY, "m1", Transfer.FIELD_TO, TaggedValue.Str("inr"))
        h.put(Transfer.ENTITY, "m1", Transfer.FIELD_SENT, TaggedValue.Num(50_000))
        h.put(Transfer.ENTITY, "m1", Transfer.FIELD_SENT_CURRENCY, TaggedValue.Str("INR"))
        h.put(Transfer.ENTITY, "m1", Transfer.FIELD_RECEIVED, TaggedValue.Num(4_500_000))
        h.put(Transfer.ENTITY, "m1", Transfer.FIELD_RECEIVED_CURRENCY, TaggedValue.Str("INR"))

        // The EUR account's balance is untouched — the leg claimed to be INR.
        val byId = r.accountBalances().associateBy { it.account.id }
        assertEquals(Money(100_000, Currency.EUR), byId.getValue("eur").balance)
        assertEquals(0, byId.getValue("eur").transferCount)
    }

    @Test
    fun `an effect is reported only for the accounts involved`() {
        val t = Transfer(
            id = "m1",
            fromAccountId = "a",
            toAccountId = "b",
            sent = Money(50_000, Currency.EUR),
            received = Money(4_500_000, Currency.INR),
            day = 20_000,
            note = null,
            deleted = false,
        )
        assertEquals(Money(-50_000, Currency.EUR), t.effectOn("a"))
        assertEquals(Money(4_500_000, Currency.INR), t.effectOn("b"))
        assertNull(t.effectOn("c"))
    }

    @Test
    fun `a note survives the round trip`() {
        val r = repo()
        r.twoAccounts()
        r.transfer(
            "m1", "eur", "inr", Money(50_000, Currency.EUR), Money(4_500_000, Currency.INR),
            day = 20_000, note = "rent home",
        )
        assertEquals("rent home", r.transfers().single().note)
    }

    @Test
    fun `transfers read back newest first`() {
        val r = repo()
        r.twoAccounts()
        r.transfer("m1", "eur", "inr", Money(1000, Currency.EUR), Money(90_000, Currency.INR), day = 20_000)
        r.transfer("m2", "eur", "inr", Money(1000, Currency.EUR), Money(90_000, Currency.INR), day = 20_005)
        assertEquals(listOf("m2", "m1"), r.transfers().map { it.id })
    }

    @Test
    fun `a transfer is not a transaction`() {
        val r = repo()
        r.twoAccounts()
        r.transfer("m1", "eur", "inr", Money(50_000, Currency.EUR), Money(4_500_000, Currency.INR), day = 20_000)
        assertTrue(r.transactions().isEmpty())
        assertTrue(r.unassigned().isEmpty())
        assertFalse(r.byDay().isNotEmpty())
    }
}
