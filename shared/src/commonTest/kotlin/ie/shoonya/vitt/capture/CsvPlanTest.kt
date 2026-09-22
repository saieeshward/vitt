package ie.shoonya.vitt.capture

import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.time.Civil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CsvPlanTest {

    private fun eur(minor: Long) = Money(minor, Currency.EUR)

    private fun result(vararg rows: CsvRow) =
        CsvImportResult(rows = rows.toList(), detectedColumns = emptyMap(), skippedLines = 0)

    private fun row(
        line: Int,
        date: String?,
        description: String? = "TESCO STORES 3421",
        amount: Money? = Money(-1250, Currency.EUR),
        problems: List<String> = emptyList(),
    ) = CsvRow(line, date, description, amount, problems)

    @Test
    fun `a readable row becomes importable`() {
        val plan = CsvPlan.of(result(row(2, "03/04/2026")), CsvDate.Order.DAY_FIRST)
        val only = plan.rows.single()
        assertTrue(only.isImportable)
        assertEquals(Civil.toDays(2026, 4, 3), only.day)
        assertEquals(eur(-1250), only.amount)
    }

    @Test
    fun `an unreadable date keeps the row out rather than filing it under today`() {
        val plan = CsvPlan.of(result(row(2, "last Tuesday")), CsvDate.Order.DAY_FIRST)
        val only = plan.rows.single()
        assertFalse(only.isImportable)
        assertTrue("date not understood" in only.problems)
        assertTrue(plan.importable.isEmpty())
    }

    @Test
    fun `an ambiguous file says so rather than picking a reading`() {
        // Nothing in the file settles the order, so every row is held back and
        // the screen has to ask. Guessing would misdate the whole import.
        val plan = CsvPlan.of(
            result(row(2, "03/04/2026"), row(3, "05/06/2026")),
            CsvDate.Order.AMBIGUOUS,
        )
        assertTrue(plan.needsDateOrder)
        assertTrue(plan.importable.isEmpty())
        assertTrue(plan.skipped.all { "date could be either way round" in it.problems })
    }

    @Test
    fun `answering the order turns the same file importable`() {
        // The caller re-plans without re-reading the file, which is why the
        // order is a parameter rather than derived inside.
        val parsed = result(row(2, "03/04/2026"), row(3, "05/06/2026"))
        assertEquals(0, CsvPlan.of(parsed, CsvDate.Order.AMBIGUOUS).importable.size)
        assertEquals(2, CsvPlan.of(parsed, CsvDate.Order.DAY_FIRST).importable.size)
    }

    @Test
    fun `a zero row is caught here rather than thrown mid-file`() {
        // record() requires a non-zero amount, and discovering that halfway
        // through writing five hundred rows would leave the import half done
        // with no way back.
        val plan = CsvPlan.of(result(row(2, "03/04/2026", amount = eur(0))), CsvDate.Order.DAY_FIRST)
        assertFalse(plan.rows.single().isImportable)
        assertTrue("zero amount" in plan.rows.single().problems)
    }

    @Test
    fun `a missing amount is a problem rather than a crash`() {
        val plan = CsvPlan.of(result(row(2, "03/04/2026", amount = null)), CsvDate.Order.DAY_FIRST)
        assertTrue("no amount" in plan.rows.single().problems)
    }

    @Test
    fun `the parser's own problems survive into the plan`() {
        val plan = CsvPlan.of(
            result(row(2, "03/04/2026", problems = listOf("unreadable column"))),
            CsvDate.Order.DAY_FIRST,
        )
        assertTrue("unreadable column" in plan.rows.single().problems)
        assertFalse(plan.rows.single().isImportable)
    }

    @Test
    fun `the merchant is cleaned the same way a shared alert is`() {
        // An imported row and a shared bank alert describing the same purchase
        // should agree, or the categoriser learns two rules for one shop.
        val plan = CsvPlan.of(result(row(2, "03/04/2026")), CsvDate.Order.DAY_FIRST)
        assertEquals(MerchantName.clean("TESCO STORES 3421"), plan.rows.single().merchant)
    }

    @Test
    fun `skipped rows are counted separately — that is the number worth reading`() {
        val plan = CsvPlan.of(
            result(row(2, "03/04/2026"), row(3, "nonsense"), row(4, "05/04/2026")),
            CsvDate.Order.DAY_FIRST,
        )
        assertEquals(2, plan.importable.size)
        assertEquals(1, plan.skipped.size)
        assertEquals(3, plan.skipped.single().lineNumber)
    }

    @Test
    fun `the day range covers only what will actually be imported`() {
        val plan = CsvPlan.of(
            result(row(2, "03/04/2026"), row(3, "nonsense"), row(4, "25/04/2026")),
            CsvDate.Order.DAY_FIRST,
        )
        assertEquals(Civil.toDays(2026, 4, 3)..Civil.toDays(2026, 4, 25), plan.dayRange)
    }

    @Test
    fun `an empty file plans to nothing without failing`() {
        val plan = CsvPlan.of(result(), CsvDate.Order.UNAMBIGUOUS)
        assertTrue(plan.importable.isEmpty())
        assertTrue(plan.dayRange == null)
        assertFalse(plan.needsDateOrder)
    }
}
