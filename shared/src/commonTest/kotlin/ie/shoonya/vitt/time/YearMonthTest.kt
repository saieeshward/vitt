package ie.shoonya.vitt.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YearMonthTest {

    @Test
    fun `the epoch is the first of January 1970`() {
        assertEquals(Triple(1970, 1, 1), Civil.fromDays(0))
        assertEquals(0, Civil.toDays(1970, 1, 1))
    }

    @Test
    fun `days and dates round-trip across a wide range`() {
        // Every 37th day for a century, which crosses every month length, every
        // leap year and the 1900/2000 century rules.
        var day = Civil.toDays(1950, 1, 1)
        val end = Civil.toDays(2050, 1, 1)
        while (day < end) {
            val (y, m, d) = Civil.fromDays(day)
            assertEquals(day, Civil.toDays(y, m, d), "round trip failed at day $day")
            day += 37
        }
    }

    @Test
    fun `a date before the epoch works`() {
        val day = Civil.toDays(1969, 12, 31)
        assertEquals(-1, day)
        assertEquals(Triple(1969, 12, 31), Civil.fromDays(day))
    }

    @Test
    fun `leap years follow the full rule — not just divisibility by four`() {
        assertEquals(29, Civil.daysInMonth(2024, 2))
        assertEquals(28, Civil.daysInMonth(2023, 2))
        // The two that a naive `year % 4` gets wrong.
        assertEquals(28, Civil.daysInMonth(1900, 2))
        assertEquals(29, Civil.daysInMonth(2000, 2))
    }

    @Test
    fun `month lengths are right`() {
        assertEquals(31, Civil.daysInMonth(2026, 1))
        assertEquals(30, Civil.daysInMonth(2026, 4))
        assertEquals(31, Civil.daysInMonth(2026, 12))
        assertFailsWith<IllegalArgumentException> { Civil.daysInMonth(2026, 13) }
    }

    @Test
    fun `a month knows its own bounds`() {
        val february = YearMonth(2024, 2)
        assertEquals(Civil.toDays(2024, 2, 1), february.firstDay)
        assertEquals(Civil.toDays(2024, 2, 29), february.lastDay)
        assertEquals(29, february.lastDay - february.firstDay + 1)
    }

    @Test
    fun `a day belongs to exactly one month`() {
        val september = YearMonth(2026, 9)
        assertTrue(Civil.toDays(2026, 9, 1) in september)
        assertTrue(Civil.toDays(2026, 9, 30) in september)
        // The boundaries either side, which is where an off-by-one would hide.
        assertFalse(Civil.toDays(2026, 8, 31) in september)
        assertFalse(Civil.toDays(2026, 10, 1) in september)
    }

    @Test
    fun `a day maps back to its month`() {
        assertEquals(YearMonth(2026, 9), YearMonth.of(Civil.toDays(2026, 9, 2)))
        assertEquals(YearMonth(2026, 9), YearMonth.of(Civil.toDays(2026, 9, 30)))
        assertEquals(YearMonth(2026, 10), YearMonth.of(Civil.toDays(2026, 10, 1)))
    }

    @Test
    fun `stepping a month crosses the year boundary`() {
        assertEquals(YearMonth(2027, 1), YearMonth(2026, 12).next())
        assertEquals(YearMonth(2026, 12), YearMonth(2027, 1).previous())
        assertEquals(YearMonth(2026, 10), YearMonth(2026, 9).next())
    }

    @Test
    fun `stepping forward and back returns the original for every month`() {
        var m = YearMonth(2025, 1)
        repeat(36) {
            assertEquals(m, m.next().previous())
            m = m.next()
        }
    }

    @Test
    fun `months order chronologically`() {
        assertTrue(YearMonth(2026, 1) < YearMonth(2026, 2))
        assertTrue(YearMonth(2026, 12) < YearMonth(2027, 1))
        assertEquals(YearMonth(2026, 9), YearMonth(2026, 9))
    }

    @Test
    fun `day of week is Monday-based — and correct at the epoch`() {
        // 1 January 1970 was a Thursday, which is index 3 when Monday is 0.
        assertEquals(3, Civil.dayOfWeek(0))
        assertEquals(0, Civil.dayOfWeek(Civil.toDays(2026, 9, 7)))   // a Monday
        assertEquals(6, Civil.dayOfWeek(Civil.toDays(2026, 9, 6)))   // the Sunday before
    }

    @Test
    fun `day of week stays in range before the epoch`() {
        // Kotlin's % keeps the dividend's sign, so this is where a naive
        // implementation returns a negative index and crashes a lookup.
        (-800..800).forEach { assertTrue(Civil.dayOfWeek(it) in 0..6, "out of range at $it") }
    }

    @Test
    fun `a week runs Monday to Sunday`() {
        val wednesday = Civil.toDays(2026, 9, 2)
        val week = Week.containing(wednesday)
        assertEquals(Civil.toDays(2026, 8, 31), week.firstDay)  // Monday
        assertEquals(Civil.toDays(2026, 9, 6), week.lastDay)     // Sunday
        assertEquals(7, week.lastDay - week.firstDay + 1)
        assertTrue(wednesday in week)
    }

    @Test
    fun `a Monday belongs to its own week and a Sunday to the week before`() {
        val monday = Civil.toDays(2026, 9, 7)
        assertEquals(monday, Week.containing(monday).firstDay)
        val sunday = Civil.toDays(2026, 9, 6)
        assertEquals(Civil.toDays(2026, 8, 31), Week.containing(sunday).firstDay)
    }

    @Test
    fun `weeks step by seven and cross year boundaries`() {
        val week = Week.containing(Civil.toDays(2026, 12, 30))
        assertEquals(week.firstDay + 7, week.next().firstDay)
        assertEquals(week.firstDay - 7, week.previous().firstDay)
        // The week containing 30 December 2026 runs into January.
        val (y, _, _) = Civil.fromDays(week.lastDay)
        assertEquals(2027, y)
    }

    @Test
    fun `every day belongs to exactly one week`() {
        var day = Civil.toDays(2025, 1, 1)
        val end = Civil.toDays(2027, 1, 1)
        while (day < end) {
            val week = Week.containing(day)
            assertTrue(day in week, "day $day not in its own week")
            assertTrue(day !in week.previous(), "day $day also in the previous week")
            assertTrue(day !in week.next(), "day $day also in the next week")
            day += 1
        }
    }

    @Test
    fun `a day period is one day`() {
        val d = Day(Civil.toDays(2026, 9, 2))
        assertEquals(d.firstDay, d.lastDay)
        assertTrue(d.firstDay in d)
        assertTrue(d.firstDay + 1 !in d)
        assertEquals(d.firstDay + 1, d.next().firstDay)
    }

    @Test
    fun `a year period spans the whole year and knows leap days`() {
        val leap = Year(2024)
        assertEquals(Civil.toDays(2024, 1, 1), leap.firstDay)
        assertEquals(Civil.toDays(2024, 12, 31), leap.lastDay)
        assertEquals(366, leap.lastDay - leap.firstDay + 1)
        assertEquals(365, Year(2026).let { it.lastDay - it.firstDay + 1 })
        assertEquals(Year(2027), Year(2026).next())
    }

    @Test
    fun `a month renders machine-plain`() {
        assertEquals("2026-09", YearMonth(2026, 9).toString())
        assertEquals("2026-12", YearMonth(2026, 12).toString())
    }

    @Test
    fun `an impossible month is rejected`() {
        assertFailsWith<IllegalArgumentException> { YearMonth(2026, 0) }
        assertFailsWith<IllegalArgumentException> { YearMonth(2026, 13) }
    }
}
