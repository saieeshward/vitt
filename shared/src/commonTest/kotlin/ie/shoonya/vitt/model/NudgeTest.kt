package ie.shoonya.vitt.model

import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.sync.EventStore
import ie.shoonya.vitt.sync.testDriver
import ie.shoonya.vitt.time.Civil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NudgeTest {

    private fun repo(): LedgerRepository {
        var t = 1_000L
        return LedgerRepository(EventStore.open(testDriver(), "a219e7a71cc18912") { t++ }) { t }
    }

    private val today = Civil.toDays(2026, 9, 17)
    private fun eur(minor: Long) = Money(minor, Currency.EUR)

    private fun LedgerRepository.pending(
        habitOn: Boolean = true,
        connected: Boolean = true,
    ) = Nudges.pending(
        transactions(), budgets(), today, habitOn, connected,
        firstDay = transactions().minOfOrNull { it.day },
    )

    @Test
    fun `an empty ledger has nothing to point at`() {
        assertTrue(repo().pending().isEmpty())
    }

    @Test
    fun `uncategorised entries come first and open splits second`() {
        val r = repo()
        r.setBudget(Currency.EUR, eur(100_000))
        r.record("a", eur(-500), day = today, merchant = "ODD SHOP")
        r.record("b", eur(-600), day = today, merchant = "ODDER SHOP")
        r.record("c", eur(-2_000), day = today - 1, category = "dining", totalPaid = eur(-4_000), splitWith = setOf("ana@x.ie"))

        val kinds = r.pending().map { it.kind }

        assertEquals(listOf(NudgeKind.REVIEW_CATEGORIES, NudgeKind.SETTLE_SPLIT), kinds)
        assertEquals(2, r.pending()[0].count)
        assertEquals("2 entries need a category", r.pending()[0].label)
    }

    @Test
    fun `a budget is only asked for a currency spent this month`() {
        val r = repo()
        r.record("a", eur(-500), day = today, category = "dining")
        r.record("b", Money(-90_000, Currency.INR), day = Civil.toDays(2024, 3, 3), category = "dining")

        val budgets = r.pending().filter { it.kind == NudgeKind.SET_BUDGET }

        assertEquals(listOf(Currency.EUR), budgets.map { it.currency })
        r.setBudget(Currency.EUR, eur(100_000))
        assertTrue(r.pending().none { it.kind == NudgeKind.SET_BUDGET })
    }

    @Test
    fun `record today fires only for someone who has been recording`() {
        val r = repo()
        r.setBudget(Currency.EUR, eur(100_000))
        // Last entry a month ago: absence is not neglect, so nothing.
        r.record("a", eur(-500), day = today - 30, category = "dining")
        assertTrue(r.pending().none { it.kind == NudgeKind.RECORD_TODAY })

        // Recording this week, not yet today.
        r.record("b", eur(-500), day = today - 2, category = "dining")
        assertTrue(r.pending().any { it.kind == NudgeKind.RECORD_TODAY })

        // The habit switch is the off switch for this too.
        assertTrue(r.pending(habitOn = false).none { it.kind == NudgeKind.RECORD_TODAY })

        // And once something is recorded today, it is gone.
        r.record("c", eur(-500), day = today, category = "dining")
        assertTrue(r.pending().none { it.kind == NudgeKind.RECORD_TODAY })
    }

    @Test
    fun `the sheet is mentioned after a week and never while connected`() {
        val r = repo()
        r.setBudget(Currency.EUR, eur(100_000))
        r.record("a", eur(-500), day = today - 3, category = "dining")
        r.record("today", eur(-500), day = today, category = "dining")
        assertTrue(r.pending(connected = false).none { it.kind == NudgeKind.CONNECT_SHEET })

        r.record("b", eur(-500), day = today - 8, category = "dining")
        assertTrue(r.pending(connected = false).any { it.kind == NudgeKind.CONNECT_SHEET })
        assertTrue(r.pending(connected = true).none { it.kind == NudgeKind.CONNECT_SHEET })
    }

    @Test
    fun `a blown budget is never a nudge`() {
        val r = repo()
        r.setBudget(Currency.EUR, eur(1_000))
        r.record("a", eur(-500_000), day = today, category = "shopping")
        assertTrue(r.pending().isEmpty())
    }
}

class HabitFiguresTest {
    private fun repo(): LedgerRepository {
        var t = 1_000L
        return LedgerRepository(EventStore.open(testDriver(), "a219e7a71cc18912") { t++ }) { t }
    }
    private val today = Civil.toDays(2026, 9, 17)
    private fun eur(minor: Long) = Money(minor, Currency.EUR)

    @Test
    fun `longest run survives a lapse and recorded days are the actual days`() {
        val r = repo()
        listOf(10, 9, 8, 7, 3, 2, 0).forEach { r.record("d$it", eur(-100), day = today - it, category = "dining") }
        assertEquals(4, r.longestRun())
        assertEquals(setOf(today - 10, today - 9, today - 8, today - 7, today - 3, today - 2, today), r.recordedDays(today))
        assertEquals(7, r.daysRecorded(today))
        // A lapse of a month leaves the longest run where it was.
        r.record("late", eur(-100), day = today + 40, category = "dining")
        assertEquals(4, r.longestRun())
    }

    @Test
    fun `an imported month buys no streak`() {
        // The whole point. A bank export carries thirty dates with it, and one
        // tap must not read as thirty days of turning up.
        val r = repo()
        (0..29).forEach {
            r.record("i$it", eur(-100), day = today - it, category = "dining", imported = true)
        }
        assertEquals(0, r.longestRun())
        assertEquals(emptySet(), r.recordedDays(today))
        assertEquals(0, r.daysRecorded(today))
    }

    @Test
    fun `a day with both a typed and an imported entry still counts`() {
        // The person was there. That the same day also arrived in a file is
        // neither their fault nor a reason to take the day away.
        val r = repo()
        r.record("imp", eur(-100), day = today, category = "dining", imported = true)
        r.record("typed", eur(-250), day = today, category = "dining")
        assertEquals(setOf(today), r.recordedDays(today))
    }

    @Test
    fun `an imported row is an ordinary row everywhere else`() {
        // Only the habit count reads the flag. A budget that ignored imported
        // spending would be worse than no budget.
        val r = repo()
        r.record("imp", eur(-1_000), day = today, category = "dining", imported = true)
        assertEquals(eur(1_000), r.ledgers().first { it.currency == Currency.EUR }.spent)
    }

    @Test
    fun `per currency days and monthly coverage never sum across currencies`() {
        val r = repo()
        r.record("e1", eur(-100), day = today, category = "dining")
        r.record("e2", eur(-100), day = today - 1, category = "dining")
        r.record("i1", Money(-100, Currency.INR), day = today, category = "dining")
        assertEquals(mapOf(Currency.EUR to 2, Currency.INR to 1), r.recordedDaysByCurrency(today))

        val months = r.recordedDaysPerMonth(today, months = 2)
        assertEquals(2, months.size)
        assertEquals(Civil.fromDays(today).let { (y, m, _) -> ie.shoonya.vitt.time.YearMonth(y, m) }, months.last().month)
        assertEquals(2, months.last().recorded)
        // Elapsed is days so far, not the whole month.
        assertEquals(17, months.last().elapsed)
        assertEquals(0, months.first().recorded)
    }
}

class NoSpendDayTest {
    private fun repo(): LedgerRepository {
        var t = 1_000L
        return LedgerRepository(EventStore.open(testDriver(), "a219e7a71cc18912") { t++ }) { t }
    }
    private val today = Civil.toDays(2026, 9, 17)
    private fun eur(minor: Long) = Money(minor, Currency.EUR)

    @Test
    fun `a day with nothing spent counts as recorded everywhere the habit looks`() {
        val r = repo()
        r.record("a", eur(-100), day = today - 2, category = "dining")
        r.markNothingSpent(today - 1)
        r.markNothingSpent(today)
        assertEquals(setOf(today - 2, today - 1, today), r.recordedDays(today))
        assertEquals(3, r.longestRun())
        assertEquals(3, r.recordedDaysPerMonth(today, 1).single().recorded)
        // And it silences the record-today nudge without touching any budget.
        val nudges = Nudges.pending(r.transactions(), r.budgets(), today, true, true, today - 2, r.noSpendDays())
        assertTrue(nudges.none { it.kind == NudgeKind.RECORD_TODAY })
        assertEquals(1, r.transactions().size)
    }
}

class InsightsShapeTest {
    private fun repo(): LedgerRepository {
        var t = 1_000L
        return LedgerRepository(EventStore.open(testDriver(), "a219e7a71cc18912") { t++ }) { t }
    }
    private fun eur(minor: Long) = Money(minor, Currency.EUR)
    private val sept = ie.shoonya.vitt.time.YearMonth(2026, 9)
    private fun d(day: Int) = Civil.toDays(2026, 9, day)

    @Test
    fun `the cumulative line runs to today and only today`() {
        val r = repo()
        r.record("a", eur(-1_000), day = d(1), category = "housing")
        r.record("b", eur(-200), day = d(3), category = "dining")
        r.record("c", eur(-999), day = d(20), category = "dining")
        val line = Insights.dailyCumulative(r.transactions(), Currency.EUR, sept, today = d(5))
        assertEquals(listOf(1_000L, 1_000L, 1_200L, 1_200L, 1_200L), line)
    }

    @Test
    fun `weekday totals start on Monday`() {
        val r = repo()
        // 2026-09-14 is a Monday.
        r.record("mon", eur(-500), day = Civil.toDays(2026, 9, 14), category = "dining")
        r.record("sun", eur(-700), day = Civil.toDays(2026, 9, 13), category = "dining")
        val week = Insights.byWeekday(r.transactions(), Currency.EUR, sept)
        assertEquals(7, week.size)
        assertEquals(500L, week[0].minor)
        assertEquals(700L, week[6].minor)
    }
}
