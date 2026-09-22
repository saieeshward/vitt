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
    /** Where sync cycles run. Outlives any screen, because a sync must too. */
    private val scope: kotlinx.coroutines.CoroutineScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
    ),
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

    /**
     * Text handed in from outside the app, waiting for the user to confirm it.
     *
     * Every capture surface lands here: the Android share sheet, the iOS share
     * extension, a Shortcut the user wrote themselves. The app never goes and
     * fetches any of it. Google Play's Sensitive Permissions policy forbids
     * deriving SMS-attributed data by other means and names notification
     * listening for bank alerts as the target, so text arrives only because
     * somebody chose to send it (`PLAN.md` §0, §6).
     *
     * A parse is an offer, never a saved entry. [AmountParser] is deliberately
     * conservative and would rather be unsure than guess a sign, because a
     * guessed sign turns a 40 refund into a 40 expense: an 80 error nobody
     * notices for weeks. So this opens a prefilled Add sheet and waits.
     */
    private val _pendingCapture =
        kotlinx.coroutines.flow.MutableStateFlow<ie.shoonya.vitt.capture.ParsedTransaction?>(null)
    val pendingCapture: kotlinx.coroutines.flow.StateFlow<ie.shoonya.vitt.capture.ParsedTransaction?> =
        _pendingCapture

    /**
     * Accepts shared text and offers it to the UI.
     *
     * The default currency is the one the last entry used, which is right far
     * more often than the device region: somebody in Dublin paying an Indian
     * bill is the case this app exists for.
     */
    fun onSharedText(text: String) {
        if (text.isBlank()) return
        val fallback = ledger.accounts()
            .firstOrNull { it.id == ledger.choice(ie.shoonya.vitt.model.Choice.LAST_ACCOUNT) }
            ?.currency
        _pendingCapture.value = ie.shoonya.vitt.capture.AmountParser.parse(text, fallback)
    }

    /** Called once the sheet has opened, so a rotation does not reopen it. */
    fun captureConsumed() {
        _pendingCapture.value = null
    }

    /**
     * A request to open the Add sheet with nothing filled in.
     *
     * What the widget's button means. A counter rather than a boolean, because
     * two taps in a row are two requests and a flag would swallow the second.
     */
    private val _openAdd = kotlinx.coroutines.flow.MutableStateFlow(0)
    val openAdd: kotlinx.coroutines.flow.StateFlow<Int> = _openAdd

    /** Called from a widget tap or a deep link. */
    fun requestAdd() {
        _openAdd.value += 1
    }

    /** Wall-clock millis, for wording like "just now". */
    fun now(): Long = now.invoke()

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
        // Seeded data means the first-run setup has nothing left to ask, and a
        // screenshot run that opened on the setup screen instead of the app
        // would be useless.
        ledger.setChoice(
            ie.shoonya.vitt.model.Choice.SETUP_DONE,
            ie.shoonya.vitt.model.Choice.SETUP_YES,
        )
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

    /**
     * A deliberately punishing amount of data, for finding what only breaks at
     * volume.
     *
     * Development only, like [seedSampleData], and through the same repository
     * for the same reason. What it is built to expose:
     *
     * - **Every currency at once.** Six ledger cards is the case the home screen
     *   was designed around two of, and JPY has exponent 0, so any code that
     *   assumes two decimal places is wrong by a factor of a hundred here and
     *   nowhere in the ordinary sample.
     * - **Eighteen months.** Period stepping, the trend chart at every grain and
     *   the year view all have something to walk through, and the month cards
     *   have to be windowed correctly rather than accidentally.
     * - **Enough rows that a linear scan shows.** The Activity list, the
     *   categoriser and the insight aggregations all run over everything.
     *
     * Seeded [Random] rather than a live one: a stress run that cannot be
     * reproduced is a bug report nobody can act on.
     */
    fun seedStressData(months: Int = 18, perDay: Int = 3) {
        if (ledger.transactions().isNotEmpty()) return
        // As in [seedSampleData]: a volume run must open on the app, not on the
        // first-run setup screen.
        ledger.setChoice(
            ie.shoonya.vitt.model.Choice.SETUP_DONE,
            ie.shoonya.vitt.model.Choice.SETUP_YES,
        )
        val rng = kotlin.random.Random(20260909)
        val today = today()
        val start = today - months * 30

        // Two accounts per currency, so the account list and the per-account
        // balances are loaded too, and transfers have somewhere to land.
        val currencies = ie.shoonya.vitt.money.Currency.entries
        currencies.forEachIndexed { c, currency ->
            ledger.openAccount(
                "stress-acc-$c-a", "${currency.code} current", currency,
                ie.shoonya.vitt.model.AccountKind.CURRENT,
                ie.shoonya.vitt.money.Money(500_000L, currency),
            )
            ledger.openAccount(
                "stress-acc-$c-b", "${currency.code} savings", currency,
                ie.shoonya.vitt.model.AccountKind.SAVINGS,
                ie.shoonya.vitt.money.Money(2_000_000L, currency),
            )
        }

        // Real-looking merchant strings, because the categoriser and the
        // merchant normaliser are part of what is under test: a thousand rows of
        // "MERCHANT 41" would exercise neither.
        val merchants = listOf(
            "TESCO STORES 3421 DUBLIN IE", "SQ *COFFEE ANGEL", "DUNNES 118",
            "LIDL 0417 CORK", "SPOTIFY AB", "AERLINGUS DUBLIN", "DUBLINBUS 4419",
            "BOOTS PHARMACY 88", "SWIGGY BANGALORE", "OLA CABS", "AMAZON IN MUMBAI",
            "UBER TRIP HELP.UBER.COM", "APPLE.COM/BILL", "NETFLIX.COM",
            "SHELL SERVICE STATION", "THE WINDING STAIR", "PRET A MANGER 402",
            "TFL TRAVEL CHARGE", "SAINSBURYS S/MKT", "STARBUCKS 0881",
        )

        var n = 0
        var day = start
        while (day <= today) {
            repeat(perDay) {
                val currency = currencies[rng.nextInt(currencies.size)]
                // A spread wide enough that the trend chart has a real shape and
                // the peak is not every bar.
                val magnitude = when (rng.nextInt(10)) {
                    0 -> rng.nextLong(50_000, 400_000)
                    in 1..3 -> rng.nextLong(5_000, 50_000)
                    else -> rng.nextLong(100, 5_000)
                }
                ledger.record(
                    id = "stress-" + n.toString().padStart(5, '0'),
                    amount = ie.shoonya.vitt.money.Money(-magnitude, currency),
                    day = day,
                    merchant = merchants[rng.nextInt(merchants.size)],
                    // Null throughout: every row goes through the tiers, which is
                    // the expensive path and therefore the one worth measuring.
                    category = null,
                    accountId = "stress-acc-${currencies.indexOf(currency)}-a",
                )
                n++
            }
            // One salary a month per currency, so the "In" figures and the inflow
            // colouring are not dead code at volume.
            if (day % 30 == 0) {
                currencies.forEachIndexed { c, currency ->
                    ledger.record(
                        id = "stress-in-$n-$c",
                        amount = ie.shoonya.vitt.money.Money(900_000L, currency),
                        day = day,
                        merchant = "SALARY PAYROLL",
                        category = null,
                        accountId = "stress-acc-$c-a",
                    )
                    n++
                }
            }
            day++
        }

        // A budget on every currency: the health bar, the room-left copy and the
        // over-budget path all want to be live on six cards at once.
        currencies.forEach { currency ->
            ledger.setBudget(currency, ie.shoonya.vitt.money.Money(1_500_000L, currency))
        }

        // Splits with a long participant list, which is where the per-member
        // fields and the settlement summary get wide.
        repeat(12) { i ->
            ledger.record(
                id = "stress-split-$i",
                amount = ie.shoonya.vitt.money.Money(-4_000L, ie.shoonya.vitt.money.Currency.EUR),
                day = today - i * 3,
                merchant = "Group dinner $i",
                category = "dining",
                accountId = "stress-acc-0-a",
                totalPaid = ie.shoonya.vitt.money.Money(-24_000L, ie.shoonya.vitt.money.Currency.EUR),
                splitWith = (0..4).map { "person$it@example.com" }.toSet(),
            )
        }

        // Cross-currency transfers, the only place a rate is ever recorded, one
        // per currency pair so observedRates() has a real table.
        currencies.forEachIndexed { c, currency ->
            val next = currencies[(c + 1) % currencies.size]
            if (currency == next) return@forEachIndexed
            ledger.transfer(
                id = "stress-transfer-$c",
                fromAccountId = "stress-acc-$c-a",
                toAccountId = "stress-acc-${currencies.indexOf(next)}-a",
                sent = ie.shoonya.vitt.money.Money(10_000L, currency),
                received = ie.shoonya.vitt.money.Money(rng.nextLong(1_000, 900_000), next),
                day = today - c,
            )
        }
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

    /**
     * The sync cycle, built fresh per call.
     *
     * Fresh rather than held, because [sheets] fetches a token per request: a
     * long-lived syncer would be a long-lived client, and a sync that started
     * before an expiry would fail partway through on a credential that was fine
     * when it began.
     */
    fun syncer(): ie.shoonya.vitt.sync.Syncer = ie.shoonya.vitt.sync.Syncer(
        store = store,
        transport = ie.shoonya.vitt.sheets.SheetsTransport(sheets()),
        now = now,
    )

    /**
     * The one scheduler, wired to the store's write hook so every local change
     * — from any screen, present or future — queues a sync without the screen
     * knowing sync exists.
     */
    val sync: ie.shoonya.vitt.sync.SyncController = ie.shoonya.vitt.sync.SyncController(
        scope = scope,
        isConnected = { auth.isSignedIn },
        syncer = ::syncer,
        store = store,
        now = now,
    ).also { controller -> store.onLocalWrite = { controller.onLocalWrite() } }

    /**
     * Runs the consent flow and, on success, the first sync — which creates the
     * spreadsheet and drains everything recorded before the user connected.
     */
    suspend fun connectGoogle(): ie.shoonya.vitt.auth.AuthResult {
        val result = auth.signIn()
        if (result is ie.shoonya.vitt.auth.AuthResult.Code) sync.onForeground()
        return result
    }

    /** Revokes the grant. Local data and the user's spreadsheet are both left alone. */
    suspend fun disconnectGoogle() {
        auth.signOut()
        sync.onDisconnected()
    }
}
