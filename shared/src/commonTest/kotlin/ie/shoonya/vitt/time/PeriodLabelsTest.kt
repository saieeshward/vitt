package ie.shoonya.vitt.time

import kotlin.test.Test
import kotlin.test.assertEquals

class PeriodLabelsTest {

    // Thursday 3 September 2026.
    private val today = Civil.toDays(2026, 9, 3)

    private fun label(period: Period?) = periodLabel(period, today)
    private fun phrase(period: Period?) = periodPhrase(period, today)

    // --- labels -----------------------------------------------------------

    @Test
    fun `the period you are standing in is named relatively`() {
        // How people actually refer to a period they are in — and the reason the
        // word "Month" never has to appear on screen.
        assertEquals("Today", label(Day(today)))
        assertEquals("This week", label(Week.containing(today)))
        assertEquals("September", label(YearMonth.of(today)))
        assertEquals("2026", label(Year(2026)))
        assertEquals("All time", label(null))
    }

    @Test
    fun `yesterday gets its own word`() {
        assertEquals("Yesterday", label(Day(today - 1)))
    }

    @Test
    fun `an older day is explicit`() {
        assertEquals("12 Aug", label(Day(Civil.toDays(2026, 8, 12))))
    }

    @Test
    fun `a day in another year says so`() {
        assertEquals("12 Aug 2025", label(Day(Civil.toDays(2025, 8, 12))))
    }

    @Test
    fun `a past week within one month is a bare day range`() {
        // Mon 24 – Sun 30 August 2026.
        val week = Week.containing(Civil.toDays(2026, 8, 26))
        assertEquals("24–30 Aug", label(week))
    }

    @Test
    fun `a week straddling two months names both`() {
        // Mon 28 September – Sun 4 October 2026.
        val week = Week.containing(Civil.toDays(2026, 9, 30))
        assertEquals("28 Sep–4 Oct", label(week))
    }

    @Test
    fun `a week in another year says so`() {
        val week = Week.containing(Civil.toDays(2025, 3, 12))
        assertEquals("10–16 Mar 2025", label(week))
    }

    @Test
    fun `a month in another year says so`() {
        assertEquals("August 2025", label(YearMonth(2025, 8)))
    }

    // --- phrases ----------------------------------------------------------

    @Test
    fun `a phrase reads inside a sentence`() {
        // Templating the label produced "out in this week": relative periods take
        // no preposition, explicit ones do.
        assertEquals("today", phrase(Day(today)))
        assertEquals("this week", phrase(Week.containing(today)))
        assertEquals("this month", phrase(YearMonth.of(today)))
        assertEquals("this year", phrase(Year(2026)))
        assertEquals("in total", phrase(null))
    }

    @Test
    fun `an explicit phrase takes a preposition`() {
        assertEquals("yesterday", phrase(Day(today - 1)))
        assertEquals("on 12 Aug", phrase(Day(Civil.toDays(2026, 8, 12))))
        assertEquals("in August", phrase(YearMonth(2026, 8)))
        assertEquals("in 2025", phrase(Year(2025)))
        assertEquals("in 24–30 Aug", phrase(Week.containing(Civil.toDays(2026, 8, 26))))
    }

    @Test
    fun `every phrase completes the sentence it is used in`() {
        // The one property that matters: "€40 out <phrase>" must read as English
        // for every grain, which means no phrase may start with a capital.
        listOf(
            Day(today), Day(today - 1), Day(Civil.toDays(2026, 8, 12)),
            Week.containing(today), Week.containing(Civil.toDays(2026, 8, 26)),
            YearMonth.of(today), YearMonth(2025, 8),
            Year(2026), Year(2025), null,
        ).forEach { period ->
            val p = phrase(period)
            assertEquals(p.lowercase().first(), p.first(), "\"$p\" starts capitalised")
        }
    }

    @Test
    fun `a month boundary does not confuse this-week with this-month`() {
        // 1 September 2026 is a Tuesday, so its week began in August. The week is
        // still "this week" and the month is still "this month".
        val firstOfMonth = Civil.toDays(2026, 9, 1)
        assertEquals("this week", periodPhrase(Week.containing(firstOfMonth), firstOfMonth))
        assertEquals("this month", periodPhrase(YearMonth.of(firstOfMonth), firstOfMonth))
        // And that week's own label, viewed from a later month, spans both.
        assertEquals("31 Aug–6 Sep", periodLabel(Week.containing(firstOfMonth), Civil.toDays(2026, 10, 5)))
    }
}
