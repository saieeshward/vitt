package ie.shoonya.vitt.notify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReminderTest {

    @Test
    fun `a stored time reads back exactly`() {
        val r = Reminder.ofCode("21:00")
        assertEquals(21, r?.hour)
        assertEquals(0, r?.minute)
        assertEquals("21:00", r?.format())
    }

    @Test
    fun `midnight and one minute to it both survive the round trip`() {
        // Zero is a real time and a falsy number, so it is the one most likely
        // to be treated as "unset" by accident somewhere downstream.
        assertEquals("00:00", Reminder.ofCode("00:00")?.format())
        assertEquals(0, Reminder.ofCode("00:00")?.minutesSinceMidnight)
        assertEquals("23:59", Reminder.ofCode("23:59")?.format())
    }

    @Test
    fun `absent means off`() {
        assertNull(Reminder.ofCode(null))
        assertNull(Reminder.ofCode(""))
    }

    @Test
    fun `an unreadable value means off rather than a default`() {
        // Substituting a default would fire a notification nobody asked for at
        // an hour they did not choose. Silence is the safe failure.
        assertNull(Reminder.ofCode("half nine"))
        assertNull(Reminder.ofCode("24:00"))
        assertNull(Reminder.ofCode("21:60"))
        assertNull(Reminder.ofCode("-1:30"))
        assertNull(Reminder.ofCode("21"))
        assertNull(Reminder.ofCode("21:00:00"))
    }

    @Test
    fun `padding is applied on the way out`() {
        assertEquals("08:05", Reminder(8 * 60 + 5).format())
    }

    @Test
    fun `every offered choice survives its own round trip`() {
        // The picker writes what it shows, so a choice that cannot be parsed
        // back would silently turn the reminder off the next time it is read.
        Reminder.CHOICES.forEach {
            assertEquals(it, Reminder.ofCode(it.format()))
        }
    }

    @Test
    fun `the default is in the evening`() {
        // A morning reminder asks about a day that has not happened yet.
        assertEquals(21, Reminder.DEFAULT.hour)
    }
}
