package ie.shoonya.vitt.model

import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.sync.EventStore
import ie.shoonya.vitt.sync.testDriver
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * How the cost of recording one transaction grows with what is already recorded.
 *
 * It should not grow at all. Appending to a log is an insert, and validating the
 * new row needs the account it names — not every event ever written. If the cost
 * per row rises with the size of the ledger then a bulk CSV import is quadratic,
 * and §6 makes import one of the three ways transactions arrive.
 *
 * Measured as a ratio between two sizes rather than against a millisecond
 * budget, because a wall-clock threshold is a flaky test on shared CI hardware
 * and the shape of the curve is the actual question.
 */
class RecordScalingTest {

    private val node = "a219e7a71cc18912"

    private fun repo(): LedgerRepository {
        var t = 1_000L
        return LedgerRepository(EventStore.open(testDriver(), node) { t++ }) { t }
    }

    /** Records [n] transactions into one account and returns the elapsed nanos. */
    private fun timeRecording(n: Int): Long {
        val ledger = repo()
        ledger.openAccount(
            "acc", "Current", Currency.EUR,
            AccountKind.CURRENT, Money(1_000_000, Currency.EUR),
        )
        val started = kotlin.time.TimeSource.Monotonic.markNow()
        repeat(n) { i ->
            ledger.record(
                id = "t" + i.toString().padStart(6, '0'),
                amount = Money(-100L - i, Currency.EUR),
                day = 20_000 + (i % 400),
                merchant = "MERCHANT ${i % 40}",
                accountId = "acc",
            )
        }
        return started.elapsedNow().inWholeMicroseconds.coerceAtLeast(1)
    }

    @Test
    fun `recording a transaction does not get slower as the ledger grows`() {
        // Warm the JIT so the first measurement is not paying for compilation.
        timeRecording(100)

        val small = timeRecording(250)
        val large = timeRecording(1_000)

        // Four times the rows. Linear work means about four times the time; the
        // per-row cost is what must stay flat.
        val smallPerRow = small / 250.0
        val largePerRow = large / 1_000.0
        val growth = largePerRow / smallPerRow

        // Generous. Quadratic growth over this 4x step is a factor of about
        // four per row, and constant work is a factor of one; anything under
        // two is comfortably not quadratic while leaving room for cache effects
        // and a noisy machine.
        assertTrue(
            growth < 2.0,
            "per-row cost grew ${growth}x from 250 to 1000 rows " +
                "(${smallPerRow}us/row then ${largePerRow}us/row) — " +
                "recording is scanning the whole log, so bulk import is quadratic",
        )
    }
}
