package ie.shoonya.vitt.notify

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApplyReminderChoiceTest {

    private class FakeScheduler(val grant: Boolean) : ReminderScheduler {
        var asked = 0
        var applied: Reminder? = null
        var appliedCount = 0
        override suspend fun requestPermission(): Boolean {
            asked++
            return grant
        }
        override fun apply(reminder: Reminder?) {
            applied = reminder
            appliedCount++
        }
    }

    private class Store {
        var value: String? = null
        var cleared = 0
    }

    @Test
    fun `granting stores the time and schedules it`() = runTest {
        val scheduler = FakeScheduler(grant = true)
        val store = Store()
        val ok = applyReminderChoice(
            picked = Reminder.DEFAULT,
            scheduler = scheduler,
            setChoice = { store.value = it },
            clearChoice = { store.cleared++ },
        )
        assertTrue(ok)
        assertEquals("21:00", store.value)
        assertEquals(Reminder.DEFAULT, scheduler.applied)
    }

    @Test
    fun `declining leaves the choice exactly as it was`() = runTest {
        // The branch that had no coverage and no way to get any. Storing it
        // hopefully would leave the picker showing a time that never fires.
        val scheduler = FakeScheduler(grant = false)
        val store = Store()
        val ok = applyReminderChoice(
            picked = Reminder.DEFAULT,
            scheduler = scheduler,
            setChoice = { store.value = it },
            clearChoice = { store.cleared++ },
        )
        assertFalse(ok)
        assertNull(store.value)
        assertEquals(0, store.cleared)
        assertEquals(0, scheduler.appliedCount)
    }

    @Test
    fun `switching off never asks for permission`() = runTest {
        // Asking to turn something off is asking for nothing, and the prompt
        // can only be spent once.
        val scheduler = FakeScheduler(grant = false)
        val store = Store()
        val ok = applyReminderChoice(
            picked = null,
            scheduler = scheduler,
            setChoice = { store.value = it },
            clearChoice = { store.cleared++ },
        )
        assertTrue(ok)
        assertEquals(0, scheduler.asked)
        assertEquals(1, store.cleared)
        assertNull(scheduler.applied)
        assertEquals(1, scheduler.appliedCount, "off must still cancel the alarm")
    }

    @Test
    fun `off clears rather than storing a blank`() = runTest {
        val scheduler = FakeScheduler(grant = true)
        val store = Store()
        store.value = "21:00"
        applyReminderChoice(null, scheduler, { store.value = it }, { store.cleared++ })
        // Choice.events refuses a blank value, so "" would throw rather than
        // turn the reminder off.
        assertEquals("21:00", store.value, "setChoice must not be called at all")
        assertEquals(1, store.cleared)
    }
}
