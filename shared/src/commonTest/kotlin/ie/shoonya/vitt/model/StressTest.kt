package ie.shoonya.vitt.model

import ie.shoonya.vitt.export.exportTransactionsCsv
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.sync.EventStore
import ie.shoonya.vitt.sync.testDriver
import ie.shoonya.vitt.time.Civil
import ie.shoonya.vitt.time.YearMonth
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The engine under a punishing amount of data.
 *
 * The unit tests elsewhere check behaviour on tidy inputs of two or three rows,
 * which is the right shape for them and says nothing about volume. These check
 * the properties that must hold no matter how much is in the log, and they exist
 * because every defect found in this app so far was found by hand.
 *
 * Six currencies rather than two throughout, because that is the case the app
 * was designed around two of. JPY carries exponent 0, so anything assuming two
 * decimal places is out by a factor of a hundred here and correct in every other
 * test file.
 *
 * The [Random] is seeded. A stress failure that cannot be reproduced is a bug
 * report nobody can act on.
 */
class StressTest {

    private val node = "a219e7a71cc18912"

    private fun repo(): LedgerRepository {
        var t = 1_000L
        return LedgerRepository(EventStore.open(testDriver(), node) { t++ }) { t }
    }

    private val currencies = Currency.entries
    private val start = Civil.toDays(2025, 4, 1)
    private val end = Civil.toDays(2026, 9, 9)

    /**
     * The corpus, built once for the whole class.
     *
     * Filling it per test meant seven independent eighteen-month writes through
     * the event store in one worker, which killed the JVM: Gradle reported only
     * `java.io.EOFException`, because the process that knew why was the one that
     * died. These tests only read, so one corpus is honest as well as faster —
     * the write path has its own tests, and `replaying the same log twice`
     * builds its own pair.
     */
    private val corpus by lazy {
        val ledger = repo()
        val expected = ledger.fill()
        ledger to expected
    }

    /**
     * Every currency, every day, for eighteen months.
     *
     * Amounts are recorded per currency into a plain map as they are written, so
     * the assertions compare the repository against a total this test computed
     * itself rather than against another call into the thing under test.
     */
    private fun LedgerRepository.fill(): Map<Currency, Long> {
        val rng = Random(20260909)
        val expected = mutableMapOf<Currency, Long>()
        var n = 0
        var day = start
        while (day <= end) {
            repeat(3) {
                val currency = currencies[rng.nextInt(currencies.size)]
                val minor = rng.nextLong(100, 400_000)
                record(
                    id = "s" + n.toString().padStart(5, '0'),
                    amount = Money(-minor, currency),
                    day = day,
                    merchant = "MERCHANT ${rng.nextInt(40)}",
                )
                expected[currency] = (expected[currency] ?: 0L) + minor
                n++
            }
            day++
        }
        return expected
    }

    @Test
    fun `spending totals per currency are exact at volume`() {
        val (ledger, expected) = corpus

        // Not a tolerance. Money is integer minor units precisely so that a
        // sum over sixteen hundred rows is the same arithmetic as a sum over
        // two, and any drift here would mean a floating point value had got
        // into an amount somewhere.
        currencies.forEach { currency ->
            val spent = ledger.transactions()
                .filter { it.amount.currency == currency && it.amount.isOutflow }
                .sumOf { -it.amount.minor }
            assertEquals(expected[currency] ?: 0L, spent, "total for ${currency.code}")
        }
    }

    @Test
    fun `no aggregation ever blends two currencies`() {
        val (ledger, _) = corpus

        // §0.6 is the constraint most easily broken by accident, because the
        // convenient thing for any aggregation to do is add up the numbers in
        // front of it. Every currency is asked for separately and each answer
        // must contain only its own.
        val rows = ledger.transactions()
        val august = YearMonth(2026, 8)
        currencies.forEach { currency ->
            Insights.byCategory(rows, currency, august).forEach {
                assertEquals(currency, it.spent.currency, "category slice currency")
            }
            Insights.topMerchants(rows, currency, august).forEach {
                assertEquals(currency, it.spent.currency, "merchant slice currency")
            }
            Insights.trend(rows, currency, august, count = 12).forEach {
                assertEquals(currency, it.spent.currency, "trend slice currency")
            }
        }
    }

    @Test
    fun `category slices sum to the ledger figure at volume`() {
        val (ledger, _) = corpus

        // The screen says "these add up to the figure above", so this is that
        // sentence as a test. It holds only because uncategorised spending is
        // kept as its own slice rather than dropped.
        val rows = ledger.transactions()
        val month = YearMonth(2026, 8)
        currencies.forEach { currency ->
            val slices = Insights.byCategory(rows, currency, month)
                .sumOf { it.spent.minor }
            // Computed here rather than read back off another Insights call, so
            // the two sides of this assertion do not share a mistake.
            val whole = rows
                .filter { it.amount.currency == currency && it.amount.isOutflow }
                .filter { it.day in month }
                .sumOf { -it.amount.minor }
            assertEquals(whole, slices, "slices vs total for ${currency.code}")
        }
    }

    @Test
    fun `JPY keeps its own exponent under the same code path as EUR`() {
        val ledger = repo()
        // 100 minor units of JPY is ¥100 and 100 minor units of EUR is €1.00.
        // The same integer, two different amounts — which is the whole reason
        // the exponent lives on the currency.
        ledger.record("jpy", Money(-100, Currency.JPY), day = end, merchant = "TOKYO METRO")
        ledger.record("eur", Money(-100, Currency.EUR), day = end, merchant = "TESCO")

        val jpy = ledger.transactions().first { it.id == "jpy" }.amount
        val eur = ledger.transactions().first { it.id == "eur" }.amount
        assertTrue(jpy.displayUnsigned().contains("100"), jpy.displayUnsigned())
        assertTrue(eur.displayUnsigned().contains("1.00"), eur.displayUnsigned())
    }

    @Test
    fun `the export carries every row and never merges two currencies onto one line`() {
        val (ledger, _) = corpus
        val rows = ledger.transactions()

        val csv = ledger.exportTransactionsCsv()
        // A header plus one line per transaction. An export that silently drops
        // rows is worse than one that fails, because the user finds out when
        // they need the file and not when they made it.
        val lines = csv.trim().split("\r\n")
        assertEquals(rows.size + 1, lines.size, "line count")

        // Each currency appears in its own column value, never two on a line.
        lines.drop(1).forEach { line ->
            val present = currencies.filter { line.contains(",${it.code},") }
            assertTrue(present.size <= 1, "two currencies on one line: $line")
        }
    }

    @Test
    fun `stepping a period across eighteen months never double counts a day`() {
        val (ledger, _) = corpus

        // Walk every month in the range and add the months up. Each transaction
        // belongs to exactly one month, so the walk has to reach the same total
        // as the unwindowed ledger — an off-by-one at a month boundary shows up
        // here and is invisible on a single month.
        val rows = ledger.transactions()
        val months = buildList {
            var (year, month, _) = Civil.fromDays(start)
            val (endYear, endMonth, _) = Civil.fromDays(end)
            while (year < endYear || (year == endYear && month <= endMonth)) {
                add(YearMonth(year, month))
                if (month == 12) { year++; month = 1 } else month++
            }
        }
        currencies.forEach { currency ->
            val walked = months.sumOf { m ->
                Insights.byCategory(rows, currency, m).sumOf { it.spent.minor }
            }
            val whole = rows
                .filter { it.amount.currency == currency && it.amount.isOutflow }
                .sumOf { -it.amount.minor }
            assertEquals(whole, walked, "month walk for ${currency.code}")
        }
    }

    @Test
    fun `replaying the same log twice reaches the same state`() {
        // Convergence, on a log big enough for an ordering mistake to surface.
        // The engine's whole claim is that the log is the truth and the state is
        // derived, which means deriving it twice has to agree.
        val first = repo()
        val expected = first.fill()
        val second = repo()
        val again = second.fill()
        assertEquals(expected, again)
        assertEquals(first.transactions().size, second.transactions().size)
        assertEquals(
            first.transactions().map { it.id to it.amount.minor },
            second.transactions().map { it.id to it.amount.minor },
        )
    }
}
