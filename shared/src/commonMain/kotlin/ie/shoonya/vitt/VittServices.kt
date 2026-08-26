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
        val day = today()
        ledger.record(
            id = "sample-1",
            amount = ie.shoonya.vitt.money.Money(-1250, ie.shoonya.vitt.money.Currency.EUR),
            day = day, merchant = "Tesco", category = "Groceries",
        )
        ledger.record(
            id = "sample-2",
            amount = ie.shoonya.vitt.money.Money(341000, ie.shoonya.vitt.money.Currency.EUR),
            day = day, merchant = "Salary", category = "Income",
        )
        ledger.record(
            id = "sample-3",
            amount = ie.shoonya.vitt.money.Money(-124000, ie.shoonya.vitt.money.Currency.INR),
            day = day - 1, merchant = "Swiggy", category = "Dining",
        )
        ledger.record(
            id = "sample-4",
            amount = ie.shoonya.vitt.money.Money(-2305, ie.shoonya.vitt.money.Currency.EUR),
            day = day - 1, merchant = "Dinner with Anya", category = "Dining",
            totalPaid = ie.shoonya.vitt.money.Money(-4610, ie.shoonya.vitt.money.Currency.EUR),
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
