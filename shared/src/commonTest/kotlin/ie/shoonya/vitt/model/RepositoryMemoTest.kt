package ie.shoonya.vitt.model

import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.sync.Event
import ie.shoonya.vitt.sync.EventStore
import ie.shoonya.vitt.sync.Hlc
import ie.shoonya.vitt.sync.TaggedValue
import ie.shoonya.vitt.sync.testDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A redraw asks for the transactions about fifteen times. These pin that it is
 * one list, rebuilt only when something it depends on is written.
 */
class RepositoryMemoTest {

    private fun open(): Pair<EventStore, LedgerRepository> {
        var t = 1_000L
        val store = EventStore.open(testDriver(), "a219e7a71cc18912") { t++ }
        return store to LedgerRepository(store) { t }
    }

    @Test
    fun `the transactions are built once per write rather than once per question`() {
        val (_, r) = open()
        r.record("t1", Money(-1250, Currency.EUR), day = 20_000)
        assertSame(r.transactions(), r.transactions())
    }

    @Test
    fun `a new entry is seen at once`() {
        val (_, r) = open()
        r.record("t1", Money(-1250, Currency.EUR), day = 20_000)
        val before = r.transactions()
        r.record("t2", Money(-500, Currency.EUR), day = 20_001)
        assertNotSame(before, r.transactions())
        assertEquals(2, r.transactions().size)
    }

    @Test
    fun `a theme change leaves the transactions alone`() {
        // Each type is keyed on its own writes, so the redraw a theme causes
        // does not rebuild a list of every transaction.
        val (_, r) = open()
        r.record("t1", Money(-1250, Currency.EUR), day = 20_000)
        val before = r.transactions()
        r.setChoice(Choice.ACCENT, "violet")
        assertSame(before, r.transactions())
        assertEquals("violet", r.choice(Choice.ACCENT))
    }

    @Test
    fun `an entry from another device is seen too`() {
        // The sync path writes through appendRemote, not record. A memo that
        // only watched local writes would hide the other phone's entries.
        val (store, r) = open()
        r.record("t1", Money(-1250, Currency.EUR), day = 20_000)
        r.transactions()
        val other = Hlc(2_000, 0, "b219e7a71cc18912")
        store.appendRemote(
            listOf(
                Event(other, Transaction.ENTITY, "t9", "amount", TaggedValue.Num(-700)),
                Event(Hlc(2_000, 1, "b219e7a71cc18912"), Transaction.ENTITY, "t9", "currency", TaggedValue.Str("EUR")),
                Event(Hlc(2_000, 2, "b219e7a71cc18912"), Transaction.ENTITY, "t9", "day", TaggedValue.Num(20_002)),
            ),
        )
        assertTrue(r.transactions().any { it.id == "t9" }, r.transactions().toString())
    }

    @Test
    fun `archived accounts are filtered from the one list`() {
        val (_, r) = open()
        r.openAccount("a1", "AIB", Currency.EUR)
        r.openAccount("a2", "Old", Currency.EUR)
        r.setAccountArchived("a2", true)
        assertEquals(listOf("a1"), r.accounts().map { it.id })
        assertEquals(listOf("a1", "a2"), r.accounts(includeArchived = true).map { it.id })
    }
}
