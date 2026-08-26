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
        val eur = ie.shoonya.vitt.money.Currency.EUR
        val inr = ie.shoonya.vitt.money.Currency.INR

        // Two weeks of a plausible month across two currencies: salary in, rent
        // out, a scatter of daily spending, and one split. Enough for the ledger
        // cards, the activity list and the owed row to each have something real.
        val seeds = listOf(
            Triple(today - 13, 341000L to eur, "Salary" to "Income"),
            Triple(today - 13, -145000L to eur, "Rent" to "Housing"),
            Triple(today - 12, -6820L to eur, "Tesco" to "Groceries"),
            Triple(today - 12, -350L to eur, "Coffee" to "Dining"),
            Triple(today - 11, -1899L to eur, "Dublin Bus" to "Transport"),
            Triple(today - 10, 1200000L to inr, "Consulting" to "Income"),
            Triple(today - 10, -18000L to inr, "Swiggy" to "Dining"),
            Triple(today - 9, -4590L to eur, "Boots" to "Health"),
            Triple(today - 8, -1250L to eur, "Spotify" to "Subscriptions"),
            Triple(today - 7, -8940L to eur, "Tesco" to "Groceries"),
            Triple(today - 6, -60000L to inr, "Ola" to "Transport"),
            Triple(today - 4, -2200L to eur, "Aer Lingus seat" to "Travel"),
            Triple(today - 3, -35000L to inr, "Amazon.in" to "Shopping"),
            Triple(today - 2, -1180L to eur, "Coffee" to "Dining"),
            Triple(today - 1, -7250L to eur, "Lidl" to "Groceries"),
            Triple(today, -420L to eur, "Coffee" to "Dining"),
        )

        seeds.forEachIndexed { i, (day, amount, what) ->
            ledger.record(
                id = "sample-" + i.toString().padStart(2, '0'),
                amount = ie.shoonya.vitt.money.Money(amount.first, amount.second),
                day = day,
                merchant = what.first,
                category = what.second,
            )
        }

        // One split, recorded separately because your share is what the budget
        // and the categories see — not what you fronted.
        ledger.record(
            id = "sample-split",
            amount = ie.shoonya.vitt.money.Money(-2305, eur),
            day = today - 5,
            merchant = "Dinner with Anya",
            category = "Dining",
            totalPaid = ie.shoonya.vitt.money.Money(-4610, eur),
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
