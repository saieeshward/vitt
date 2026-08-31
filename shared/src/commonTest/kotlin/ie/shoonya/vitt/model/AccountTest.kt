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
import kotlin.test.assertTrue

class AccountTest {

    private val node = "a219e7a71cc18912"

    private fun repo(): LedgerRepository = harness().repo

    /**
     * A repository together with the store behind it.
     *
     * Two tests need to write events the repository deliberately refuses to
     * write — the shapes only a hand-edited sheet row can produce. Reaching the
     * store directly keeps that capability out of the production API.
     */
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

    @Test
    fun `an opened account reads back`() {
        val r = repo()
        r.openAccount("a1", "Revolut", Currency.EUR, AccountKind.CURRENT)
        val a = r.accounts().single()
        assertEquals("Revolut", a.name)
        assertEquals(Currency.EUR, a.currency)
        assertEquals(AccountKind.CURRENT, a.kind)
        assertEquals(0L, a.opening.minor)
        assertFalse(a.archived)
    }

    @Test
    fun `an opening balance starts the account off zero`() {
        val r = repo()
        r.openAccount("a1", "AIB", Currency.EUR, opening = Money(50_000, Currency.EUR))
        r.record("t1", Money(-1250, Currency.EUR), day = 20_000, accountId = "a1")
        assertEquals(48_750L, r.accountBalances().single().balance.minor)
    }

    @Test
    fun `an opening balance in another currency is rejected`() {
        val r = repo()
        assertFailsWith<IllegalArgumentException> {
            r.openAccount("a1", "AIB", Currency.EUR, opening = Money(50_000, Currency.INR))
        }
    }

    @Test
    fun `a balance is the opening plus the account's own transactions`() {
        val r = repo()
        r.openAccount("a1", "AIB", Currency.EUR)
        r.record("t1", Money(-1250, Currency.EUR), day = 20_000, accountId = "a1")
        r.record("t2", Money(-750, Currency.EUR), day = 20_001, accountId = "a1")
        r.record("t3", Money(200_000, Currency.EUR), day = 20_002, accountId = "a1")

        val b = r.accountBalances().single()
        assertEquals(198_000L, b.balance.minor)
        assertEquals(3, b.transactionCount)
    }

    @Test
    fun `another account's transactions do not reach this balance`() {
        val r = repo()
        r.openAccount("a1", "AIB", Currency.EUR)
        r.openAccount("a2", "HDFC", Currency.INR)
        r.record("t1", Money(-1250, Currency.EUR), day = 20_000, accountId = "a1")
        r.record("t2", Money(-90_000, Currency.INR), day = 20_000, accountId = "a2")

        val balances = r.accountBalances().associateBy { it.account.id }
        assertEquals(-1250L, balances.getValue("a1").balance.minor)
        assertEquals(Currency.EUR, balances.getValue("a1").balance.currency)
        assertEquals(-90_000L, balances.getValue("a2").balance.minor)
        assertEquals(Currency.INR, balances.getValue("a2").balance.currency)
    }

    @Test
    fun `an unassigned transaction is in no account balance but is surfaced`() {
        val r = repo()
        r.openAccount("a1", "AIB", Currency.EUR)
        r.record("t1", Money(-1250, Currency.EUR), day = 20_000, accountId = "a1")
        r.record("t2", Money(-500, Currency.EUR), day = 20_000)

        assertEquals(-1250L, r.accountBalances().single().balance.minor)
        assertEquals(listOf("t2"), r.unassigned().map { it.id })
    }

    @Test
    fun `a hand-edited transaction in the wrong currency is ignored — not coerced`() {
        // Only reachable from a sheet row someone edited by hand: `record`
        // refuses this combination at the source.
        val h = harness()
        val r = h.repo
        r.openAccount("a1", "AIB", Currency.EUR)
        r.record("t1", Money(-1250, Currency.EUR), day = 20_000, accountId = "a1")
        h.put(Transaction.ENTITY, "t2", Transaction.FIELD_AMOUNT, TaggedValue.Num(-90_000))
        h.put(Transaction.ENTITY, "t2", Transaction.FIELD_CURRENCY, TaggedValue.Str("INR"))
        h.put(Transaction.ENTITY, "t2", Transaction.FIELD_DAY, TaggedValue.Num(20_000))
        h.put(Transaction.ENTITY, "t2", Transaction.FIELD_ACCOUNT, TaggedValue.Str("a1"))

        val b = r.accountBalances().single()
        assertEquals(-1250L, b.balance.minor)
        assertEquals(Currency.EUR, b.balance.currency)
        assertEquals(1, b.transactionCount)
    }

    @Test
    fun `recording into an account of another currency is refused`() {
        val r = repo()
        r.openAccount("a1", "AIB", Currency.EUR)
        assertFailsWith<IllegalArgumentException> {
            r.record("t1", Money(-90_000, Currency.INR), day = 20_000, accountId = "a1")
        }
    }

    @Test
    fun `recording into an account this device has not seen yet is allowed`() {
        // The account event may still be in flight from another device. Refusing
        // would lose the transaction; the balance reconciles when it arrives.
        val r = repo()
        r.record("t1", Money(-1250, Currency.EUR), day = 20_000, accountId = "unknown")
        assertEquals(1, r.transactions().size)
    }

    @Test
    fun `archiving hides an account from the pickers but keeps its history`() {
        val r = repo()
        r.openAccount("a1", "Old card", Currency.EUR)
        r.record("t1", Money(-1250, Currency.EUR), day = 20_000, accountId = "a1")
        r.setAccountArchived("a1", true)

        assertTrue(r.accounts().isEmpty())
        assertEquals(1, r.transactions().size)

        val archived = r.accounts(includeArchived = true).single()
        assertTrue(archived.archived)
        assertEquals(-1250L, r.accountBalances(includeArchived = true).single().balance.minor)
    }

    @Test
    fun `unarchiving brings an account back`() {
        val r = repo()
        r.openAccount("a1", "AIB", Currency.EUR)
        r.setAccountArchived("a1", true)
        r.setAccountArchived("a1", false)
        assertEquals("AIB", r.accounts().single().name)
    }

    @Test
    fun `renaming keeps the balance and the id`() {
        val r = repo()
        r.openAccount("a1", "AIB", Currency.EUR)
        r.record("t1", Money(-1250, Currency.EUR), day = 20_000, accountId = "a1")
        r.renameAccount("a1", "AIB — current")

        val b = r.accountBalances().single()
        assertEquals("AIB — current", b.account.name)
        assertEquals("a1", b.account.id)
        assertEquals(-1250L, b.balance.minor)
    }

    @Test
    fun `a rename and an archive from two devices both win`() {
        val r = repo()
        r.openAccount("a1", "AIB", Currency.EUR)
        r.renameAccount("a1", "AIB Current")
        r.setAccountArchived("a1", true)

        val a = r.accounts(includeArchived = true).single()
        assertEquals("AIB Current", a.name)
        assertTrue(a.archived)
    }

    @Test
    fun `archived accounts sort last`() {
        val r = repo()
        r.openAccount("a1", "First", Currency.EUR)
        r.openAccount("a2", "Second", Currency.EUR)
        r.openAccount("a3", "Third", Currency.EUR)
        r.setAccountArchived("a1", true)

        assertEquals(
            listOf("Second", "Third", "First"),
            r.accounts(includeArchived = true).map { it.name },
        )
    }

    @Test
    fun `a credit card in debt reads as owed rather than negative`() {
        val r = repo()
        r.openAccount("a1", "Visa", Currency.EUR, AccountKind.CREDIT)
        r.record("t1", Money(-12_000, Currency.EUR), day = 20_000, accountId = "a1")

        val b = r.accountBalances().single()
        assertTrue(b.owed)
        assertEquals(12_000L, b.display.minor)
        // The stored arithmetic is untouched — only the reading flips.
        assertEquals(-12_000L, b.balance.minor)
    }

    @Test
    fun `a credit card in credit is not owed`() {
        val r = repo()
        r.openAccount("a1", "Visa", Currency.EUR, AccountKind.CREDIT)
        r.record("t1", Money(5_000, Currency.EUR), day = 20_000, accountId = "a1")

        val b = r.accountBalances().single()
        assertFalse(b.owed)
        assertEquals(5_000L, b.display.minor)
    }

    @Test
    fun `an overdrawn current account is negative — not owed`() {
        val r = repo()
        r.openAccount("a1", "AIB", Currency.EUR, AccountKind.CURRENT)
        r.record("t1", Money(-12_000, Currency.EUR), day = 20_000, accountId = "a1")

        val b = r.accountBalances().single()
        assertFalse(b.owed)
        assertEquals(-12_000L, b.display.minor)
    }

    @Test
    fun `an account with no currency is dropped rather than crashing the list`() {
        val h = harness()
        val r = h.repo
        r.openAccount("a1", "Good", Currency.EUR)
        h.put(Account.ENTITY, "a2", Account.FIELD_NAME, TaggedValue.Str("Nameless currency"))
        assertEquals(listOf("Good"), r.accounts().map { it.name })
    }

    @Test
    fun `an unknown account kind degrades to OTHER and keeps the account`() {
        val h = harness()
        val r = h.repo
        r.openAccount("a1", "Future", Currency.EUR)
        h.put(Account.ENTITY, "a1", Account.FIELD_KIND, TaggedValue.Str("crypto-vault"))

        val a = r.accounts().single()
        assertEquals(AccountKind.OTHER, a.kind)
        assertEquals("Future", a.name)
        assertEquals(Currency.EUR, a.currency)
    }

    @Test
    fun `account kind codes are stable`() {
        // These strings are persisted into the log and read by other devices.
        // Renaming an enum constant must not change them.
        assertEquals(
            listOf("current", "savings", "cash", "credit", "other"),
            AccountKind.entries.map { it.code },
        )
        AccountKind.entries.forEach {
            assertEquals(it, AccountKind.ofCode(it.code))
        }
    }
}
