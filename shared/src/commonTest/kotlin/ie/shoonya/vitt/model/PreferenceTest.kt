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

class PreferenceTest {

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

    @Test
    fun `gamification is on until it is turned off`() {
        // Unset means the default, not off: the switch is about being able to
        // decline, not about having to opt in.
        val r = repo()
        assertTrue(r.gamificationEnabled())
        assertTrue(r.preferences().isEmpty())
    }

    @Test
    fun `turning it off sticks`() {
        val r = repo()
        r.setGamificationEnabled(false)
        assertFalse(r.gamificationEnabled())
    }

    @Test
    fun `it can be turned back on`() {
        val r = repo()
        r.setGamificationEnabled(false)
        r.setGamificationEnabled(true)
        assertTrue(r.gamificationEnabled())
    }

    @Test
    fun `setting it twice edits one row rather than piling up`() {
        // The key is the entity id, so this is an edit however many devices set
        // it and however often.
        val r = repo()
        r.setGamificationEnabled(false)
        r.setGamificationEnabled(false)
        assertEquals(1, r.preferences().size)
    }

    @Test
    fun `the preference travels in the log so a second device honours it`() {
        // Device-local storage would let the layer come back on a new phone,
        // which is the app overriding a decision already made.
        val h = harness()
        h.repo.setGamificationEnabled(false)

        // A second repository over the same store stands in for another device
        // that has synced the log.
        val other = LedgerRepository(h.store, h.now)
        assertFalse(other.gamificationEnabled())
    }

    @Test
    fun `a blank key is refused`() {
        val r = repo()
        assertFailsWith<IllegalArgumentException> { r.setPreference("   ", false) }
    }

    @Test
    fun `a hand-edited preference row with no value is ignored`() {
        val h = harness()
        h.put(Preference.ENTITY, Preference.GAMIFICATION, "something_else", TaggedValue.Str("x"))
        assertTrue(h.repo.preferences().isEmpty())
        // And the default still applies.
        assertTrue(h.repo.gamificationEnabled())
    }

    @Test
    fun `turning it off changes nothing about the money`() {
        // Stated in the settings copy, so it had better be true.
        val r = repo()
        r.record("t1", Money(-1250, Currency.EUR), day = 20_000, merchant = "TESCO")
        r.setBudget(Currency.EUR, Money(100_000, Currency.EUR))
        val before = r.ledgers()
        val transactionsBefore = r.transactions()

        r.setGamificationEnabled(false)

        assertEquals(before, r.ledgers())
        assertEquals(transactionsBefore, r.transactions())
        assertEquals(Money(100_000, Currency.EUR), r.budgets()[Currency.EUR]?.limit)
    }

    @Test
    fun `the days-recorded figure is still computable but the caller decides`() {
        // The repository does not hide the number when the layer is off — the UI
        // stops asking for it. Keeping the two separate means the preference
        // cannot silently corrupt a figure some other feature wants.
        val r = repo()
        r.record("t1", Money(-1250, Currency.EUR), day = 20_000)
        r.setGamificationEnabled(false)
        assertEquals(1, r.daysRecorded(today = 20_000))
    }
}
