package ie.shoonya.vitt

import ie.shoonya.vitt.auth.AuthManager
import ie.shoonya.vitt.auth.BrowserAuth
import ie.shoonya.vitt.auth.TokenStore
import ie.shoonya.vitt.auth.platformTokenStore
import ie.shoonya.vitt.auth.platformClientId
import ie.shoonya.vitt.net.platformHttpClient
import ie.shoonya.vitt.sheets.LiveVerification
import ie.shoonya.vitt.model.LedgerRepository
import ie.shoonya.vitt.sheets.SheetsClient
import ie.shoonya.vitt.sync.DeviceIdentity
import ie.shoonya.vitt.sync.EventStore
import ie.shoonya.vitt.sync.installMarker

/**
 * The composition root for everything the UI needs.
 *
 * Exists so the HTTP client stays an implementation detail: the UI layer should
 * not know which networking library is in use, and leaking it would make Ktor
 * part of this module's public API and therefore hard to replace.
 */
class VittServices(
    tokenStore: TokenStore = platformTokenStore(),
    browser: BrowserAuth,
    private val now: () -> Long,
    driver: app.cash.sqldelight.db.SqlDriver,
) {
    /**
     * The local database, keyed to this device.
     *
     * The node id is minted here and re-minted if the database arrived from a
     * backup, before anything can be written under a borrowed identity.
     */
    val store: EventStore = run {
        val bootstrap = EventStore(driver)
        val nodeId = DeviceIdentity.forStore(bootstrap, installMarker())
        EventStore.open(driver, nodeId, now)
    }

    val ledger: LedgerRepository = LedgerRepository(store, now)

    /** Days since the Unix epoch, in UTC. A date, with no time and no zone. */
    fun today(): Int = (now() / 86_400_000L).toInt()

    /**
     * Records a handful of transactions so populated screens can be inspected.
     *
     * Development only, reached from a launch variable. It writes through the
     * ordinary repository rather than seeding the database directly, so what
     * appears on screen is produced by the same path a real entry takes — a
     * fixture that bypassed the event log would prove nothing about the app.
     */
    fun seedSampleData() {
        if (ledger.transactions().isNotEmpty()) return
        val today = today()
        // Spread across the *current* month, not the last fortnight. The ledger
        // cards are monthly, so dating the sample two weeks back leaves them
        // reading near-zero for the first half of every month — which defeats the
        // one purpose this function has.
        val monthStart = ie.shoonya.vitt.time.YearMonth.of(today).firstDay
        val elapsed = (today - monthStart + 1).coerceAtLeast(1)
        fun day(index: Int) = monthStart + (index % elapsed)
        val eur = ie.shoonya.vitt.money.Currency.EUR
        val inr = ie.shoonya.vitt.money.Currency.INR

        // Accounts first: `record` checks a transaction's currency against its
        // account, so they have to exist before anything references them.
        ledger.openAccount(
            "sample-acc-aib", "AIB current", eur,
            ie.shoonya.vitt.model.AccountKind.CURRENT,
            ie.shoonya.vitt.money.Money(214_500, eur),
        )
        ledger.openAccount(
            "sample-acc-revolut", "Revolut", eur,
            ie.shoonya.vitt.model.AccountKind.CURRENT,
            ie.shoonya.vitt.money.Money(38_000, eur),
        )
        ledger.openAccount(
            "sample-acc-visa", "Visa", eur,
            ie.shoonya.vitt.model.AccountKind.CREDIT,
            ie.shoonya.vitt.money.Money(-42_150, eur),
        )
        ledger.openAccount(
            "sample-acc-hdfc", "HDFC savings", inr,
            ie.shoonya.vitt.model.AccountKind.SAVINGS,
            ie.shoonya.vitt.money.Money(1_450_000, inr),
        )

        // Two weeks of a plausible month across two currencies: salary in, rent
        // out, a scatter of daily spending, and one split. Enough for the ledger
        // cards, the activity list and the owed row to each have something real.
        val seeds = listOf(
            Triple(day(0), 341000L to eur, "SALARY PAYROLL" to null),
            Triple(day(0), -145000L to eur, "KILKENNY DESIGN CENTRE" to null),
            Triple(day(1), -6820L to eur, "TESCO STORES 3421 DUBLIN IE" to null),
            Triple(day(1), -350L to eur, "SQ *COFFEE ANGEL" to null),
            // Left uncategorised on purpose: the third tier is to ask, and the
            // review path needs something to show.
            Triple(day(2), -3400L to eur, "THE WINDING STAIR" to null),
            Triple(day(2), -1899L to eur, "DUBLINBUS 4419" to null),
            Triple(day(3), 1200000L to inr, "CONSULTING INVOICE" to null),
            Triple(day(3), -18000L to inr, "SWIGGY BANGALORE" to null),
            Triple(day(4), -4590L to eur, "BOOTS PHARMACY 88" to null),
            Triple(day(5), -1250L to eur, "SPOTIFY AB" to null),
            Triple(day(6), -8940L to eur, "TESCO STORES 3421 DUBLIN IE" to null),
            Triple(day(7), -60000L to inr, "OLA CABS" to null),
            Triple(day(9), -2200L to eur, "AERLINGUS DUBLIN" to null),
            Triple(day(10), -35000L to inr, "AMAZON IN MUMBAI" to null),
            Triple(day(11), -1180L to eur, "SQ *COFFEE ANGEL" to null),
            Triple(day(12), -7250L to eur, "LIDL 0417 CORK" to null),
            Triple(day(13), -420L to eur, "SQ *COFFEE ANGEL" to null),
        )

        seeds.forEachIndexed { i, (day, amount, what) ->
            val currency = amount.second
            ledger.record(
                id = "sample-" + i.toString().padStart(2, '0'),
                amount = ie.shoonya.vitt.money.Money(amount.first, currency),
                day = day,
                merchant = what.first,
                // Null lets the tiers run, which is the point of the sample.
                category = what.second,
                // Most entries land in an account; a couple deliberately do not,
                // so the unassigned case is visible rather than theoretical.
                accountId = when {
                    currency == inr -> "sample-acc-hdfc"
                    what.second == "Subscriptions" -> "sample-acc-visa"
                    i % 5 == 3 -> null
                    else -> "sample-acc-aib"
                },
            )
        }

        // One split, recorded separately because your share is what the budget
        // and the categories see — not what you fronted.
        ledger.record(
            id = "sample-split",
            amount = ie.shoonya.vitt.money.Money(-2305, eur),
            day = day(8),
            merchant = "Dinner with Anya",
            category = "dining",
            accountId = "sample-acc-revolut",
            totalPaid = ie.shoonya.vitt.money.Money(-4610, eur),
            splitWith = setOf("anya@example.com"),
        )

        // A second split, partly repaid, so the settlement path has something to
        // show that is neither untouched nor closed.
        ledger.record(
            id = "sample-split-2",
            amount = ie.shoonya.vitt.money.Money(-3000, eur),
            day = day(4),
            merchant = "Taxi to airport",
            category = "transport",
            accountId = "sample-acc-aib",
            totalPaid = ie.shoonya.vitt.money.Money(-9000, eur),
            splitWith = setOf("anya@example.com", "dev@example.com"),
        )
        ledger.settle("sample-split-2", ie.shoonya.vitt.money.Money(2000, eur))

        // Two transfers: one across currencies, which is the only place a rate is
        // ever recorded, and one within a currency that lost money to a fee.
        ledger.transfer(
            id = "sample-transfer-1",
            fromAccountId = "sample-acc-aib",
            toAccountId = "sample-acc-hdfc",
            sent = ie.shoonya.vitt.money.Money(50_000, eur),
            received = ie.shoonya.vitt.money.Money(4_455_000, inr),
            day = day(6),
            note = "rent home",
        )
        // A budget per currency, so the ledger cards' health bar and room-left
        // copy are exercised rather than sitting behind a null.
        ledger.setBudget(eur, ie.shoonya.vitt.money.Money(180_000, eur))
        ledger.setBudget(inr, ie.shoonya.vitt.money.Money(4_000_000, inr))

        ledger.transfer(
            id = "sample-transfer-2",
            fromAccountId = "sample-acc-aib",
            toAccountId = "sample-acc-revolut",
            sent = ie.shoonya.vitt.money.Money(20_000, eur),
            received = ie.shoonya.vitt.money.Money(19_950, eur),
            day = day(11),
        )
    }

    private val http = SheetsClient.configure(platformHttpClient())

    val auth: AuthManager = AuthManager(
        http = http,
        tokens = tokenStore,
        browser = browser,
        clientId = platformClientId(),
        now = now,
    )

    /**
     * A Sheets client that fetches a fresh token per request, so a long-running
     * sync cannot fail partway through on an expired credential.
     */
    fun sheets(): SheetsClient = SheetsClient(
        http = http,
        accessToken = { auth.accessToken() ?: "" },
        forceRefresh = { auth.forceRefresh() },
    )

    fun liveVerification(): LiveVerification = LiveVerification(sheets())
}
