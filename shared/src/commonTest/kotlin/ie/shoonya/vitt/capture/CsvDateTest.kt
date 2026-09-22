package ie.shoonya.vitt.capture

import ie.shoonya.vitt.time.Civil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CsvDateTest {

    private fun d(y: Int, m: Int, day: Int) = Civil.toDays(y, m, day)

    // --- deciding the order from the file ---

    @Test
    fun `one unambiguous row settles the whole file`() {
        // 13 cannot be a month, so the file is day-first and every other row
        // follows. A bank does not change format halfway down its own export.
        assertEquals(
            CsvDate.Order.DAY_FIRST,
            CsvDate.order(listOf("03/04/2026", "13/04/2026", "05/04/2026")),
        )
        assertEquals(
            CsvDate.Order.MONTH_FIRST,
            CsvDate.order(listOf("03/04/2026", "04/13/2026")),
        )
    }

    @Test
    fun `a file that never settles itself is ambiguous — not guessed`() {
        // Every row readable both ways. Guessing would silently misdate the lot
        // and move transactions between months.
        assertEquals(
            CsvDate.Order.AMBIGUOUS,
            CsvDate.order(listOf("03/04/2026", "05/06/2026", "01/02/2026")),
        )
    }

    @Test
    fun `an ISO file needs no order at all`() {
        assertEquals(
            CsvDate.Order.UNAMBIGUOUS,
            CsvDate.order(listOf("2026-04-03", "2026-04-13")),
        )
    }

    @Test
    fun `a file with no dates is not ambiguous`() {
        assertEquals(CsvDate.Order.UNAMBIGUOUS, CsvDate.order(listOf(null, "", "n/a")))
    }

    // --- reading a row ---

    @Test
    fun `ISO reads the same whatever the order says`() {
        val expected = d(2026, 4, 3)
        assertEquals(expected, CsvDate.parse("2026-04-03", CsvDate.Order.DAY_FIRST))
        assertEquals(expected, CsvDate.parse("2026-04-03", CsvDate.Order.MONTH_FIRST))
        assertEquals(expected, CsvDate.parse("2026/04/03", CsvDate.Order.AMBIGUOUS))
    }

    @Test
    fun `the same text reads as two different days under the two orders`() {
        // The whole point. Getting this backwards moves a transaction a month.
        assertEquals(d(2026, 4, 3), CsvDate.parse("03/04/2026", CsvDate.Order.DAY_FIRST))
        assertEquals(d(2026, 3, 4), CsvDate.parse("03/04/2026", CsvDate.Order.MONTH_FIRST))
    }

    @Test
    fun `a row that settles itself beats the file order`() {
        // 25 cannot be a month, so this row is right even in a month-first file.
        assertEquals(d(2026, 4, 25), CsvDate.parse("25/04/2026", CsvDate.Order.MONTH_FIRST))
    }

    @Test
    fun `an unresolved ambiguous row is refused rather than filed under a guess`() {
        assertNull(CsvDate.parse("03/04/2026", CsvDate.Order.AMBIGUOUS))
    }

    @Test
    fun `two-digit years are this century`() {
        assertEquals(d(2026, 4, 3), CsvDate.parse("03/04/26", CsvDate.Order.DAY_FIRST))
    }

    @Test
    fun `separators vary between banks and all three are read`() {
        val expected = d(2026, 4, 3)
        assertEquals(expected, CsvDate.parse("03/04/2026", CsvDate.Order.DAY_FIRST))
        assertEquals(expected, CsvDate.parse("03-04-2026", CsvDate.Order.DAY_FIRST))
        assertEquals(expected, CsvDate.parse("03.04.2026", CsvDate.Order.DAY_FIRST))
    }

    @Test
    fun `an impossible date is refused rather than rolled over`() {
        // 31 February must not quietly become 3 March.
        assertNull(CsvDate.parse("31/02/2026", CsvDate.Order.DAY_FIRST))
        assertNull(CsvDate.parse("00/04/2026", CsvDate.Order.DAY_FIRST))
        // Impossible under either reading: no thirteenth month, either way round.
        assertNull(CsvDate.parse("13/13/2026", CsvDate.Order.MONTH_FIRST))
        // But 03/13 under month-first is a real date — the 13th of March — and
        // must not be refused just because it looks odd.
        assertEquals(d(2026, 3, 13), CsvDate.parse("03/13/2026", CsvDate.Order.MONTH_FIRST))
    }

    @Test
    fun `a leap day is real in a leap year and not otherwise`() {
        assertEquals(d(2028, 2, 29), CsvDate.parse("29/02/2028", CsvDate.Order.DAY_FIRST))
        assertNull(CsvDate.parse("29/02/2026", CsvDate.Order.DAY_FIRST))
    }

    @Test
    fun `nonsense is null rather than today`() {
        // A row whose date cannot be read belongs in the review pile, not filed
        // under whatever day the import happened to run.
        assertNull(CsvDate.parse(null, CsvDate.Order.DAY_FIRST))
        assertNull(CsvDate.parse("", CsvDate.Order.DAY_FIRST))
        assertNull(CsvDate.parse("last Tuesday", CsvDate.Order.DAY_FIRST))
    }

    @Test
    fun `a trailing time of day is ignored rather than rejected`() {
        // Plenty of exports carry a timestamp, and the day is all this app has.
        assertEquals(d(2026, 4, 3), CsvDate.parse("2026-04-03 14:22:05", CsvDate.Order.UNAMBIGUOUS))
        assertEquals(d(2026, 4, 3), CsvDate.parse("03/04/2026 14:22", CsvDate.Order.DAY_FIRST))
    }
}
