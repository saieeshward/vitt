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
