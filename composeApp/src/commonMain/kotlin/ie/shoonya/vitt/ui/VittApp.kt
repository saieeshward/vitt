package ie.shoonya.vitt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ie.shoonya.vitt.export.exportTransactionsCsv
import ie.shoonya.vitt.export.exportTransfersCsv
import ie.shoonya.vitt.model.Choice
import ie.shoonya.vitt.model.Insights
import ie.shoonya.vitt.model.Nudge
import ie.shoonya.vitt.model.NudgeKind
import ie.shoonya.vitt.model.Nudges
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import ie.shoonya.vitt.model.LedgerRepository
import ie.shoonya.vitt.model.SettlementSummary
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.time.Civil
import ie.shoonya.vitt.time.Period
import ie.shoonya.vitt.time.YearMonth
import ie.shoonya.vitt.ui.Mood
import ie.shoonya.vitt.ui.platform.ExportFile
import ie.shoonya.vitt.ui.platform.rememberFileExporter
import ie.shoonya.vitt.ui.screens.AccountSheet
import ie.shoonya.vitt.ui.screens.ActivityFilter
import ie.shoonya.vitt.ui.screens.ActivityScreen
import ie.shoonya.vitt.ui.screens.AddScreen
import ie.shoonya.vitt.ui.screens.BudgetSheet
import ie.shoonya.vitt.ui.screens.CategorySheet
import ie.shoonya.vitt.ui.screens.EditAccountSheet
import ie.shoonya.vitt.ui.screens.GrainSheet
import ie.shoonya.vitt.ui.screens.HabitScreen
import ie.shoonya.vitt.ui.screens.ImportSheet
import ie.shoonya.vitt.ui.screens.LedgersScreen
import ie.shoonya.vitt.ui.screens.PeopleScreen
import ie.shoonya.vitt.ui.screens.ReportsSheet
import ie.shoonya.vitt.ui.screens.SettingsSheet
import ie.shoonya.vitt.ui.screens.SetupScreen
import ie.shoonya.vitt.ui.screens.SheetActions
import ie.shoonya.vitt.sync.SyncStatus
import ie.shoonya.vitt.ui.screens.SplitSheet
import ie.shoonya.vitt.ui.screens.TransferSheet
import ie.shoonya.vitt.ui.theme.AccentChoice
import ie.shoonya.vitt.theme.Appearance
import ie.shoonya.vitt.model.whereTo
import ie.shoonya.vitt.ui.theme.ThemeChoice
import ie.shoonya.vitt.ui.theme.Vitt

private enum class Tab(val label: String, val icon: VittIcon) {
    Ledgers("Ledgers", VittIcon.Wallet),
    Activity("Activity", VittIcon.List),
    People("People", VittIcon.People),
    // Reports earned the tab and Habit moved to the header. Analytics is a
    // weekly visit in a money app and the habit view a glance, which is the
    // opposite of where `design-identity.md` first put them; the identity's
    // rule — the daily path stays short — is unchanged.
    Reports("Reports", VittIcon.Chart),
}

/** Which bottom sheet is open. Only one can be, so this is a state, not four flags. */
private sealed interface Sheet {
    data object Add : Sheet
    data object NewAccount : Sheet
    data object Transfer : Sheet
    data class Split(val id: String) : Sheet
    data class Summary(val subject: String, val body: String) : Sheet
    data class SetBudget(val currency: Currency) : Sheet
    data class EditCategory(val id: String) : Sheet
    data class EditAccount(val id: String) : Sheet
    data object Import : Sheet
    data object Settings : Sheet
    data object Habit : Sheet
    data object PickGrain : Sheet
}

/**
 * The app shell.
 *
 * Four tabs and a centre button. **Add is not a tab**, because logging is not a
 * destination — it is a sheet reachable in one thumb-stretch from anywhere, and
 * putting it in the tab bar would make the daily path a navigation.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun VittApp(
    repository: LedgerRepository,
    today: Int,
    newId: () -> String,
    /**
     * Applies a theme or accent the user just picked.
     *
     * The palette is owned above this composable, because [VittTheme] wraps it:
     * a theme change has to re-enter composition from outside, not from inside
     * the tree being repainted.
     */
    onAppearanceChange: () -> Unit = {},
    /**
     * A parse waiting to be confirmed, from the share sheet or a Shortcut.
     *
     * Opens the Add sheet filled in. Never saves on its own: the parser would
     * rather be unsure than guess a sign, and a wrong sign is an error nobody
     * notices for weeks.
     */
    pendingCapture: ie.shoonya.vitt.capture.ParsedTransaction? = null,
    onCaptureConsumed: () -> Unit = {},
    /** Bumped by the widget's button. Opens the Add sheet with nothing filled in. */
    openAddTick: Int = 0,
    /** Where the sheet stands, for Settings. Off when the app runs local-only. */
    syncStatus: SyncStatus = SyncStatus.Off,
    /** The last rewrite of the spreadsheet's readable tab. Surfaced in Settings. */
    derivedTabs: ie.shoonya.vitt.sheets.DerivedTabs.Outcome? = null,
    sheetActions: SheetActions = SheetActions({ ie.shoonya.vitt.auth.AuthResult.Cancelled }, {}, {}),
    /**
     * Bumped when a sync brought events in from another device, so the
     * screens re-read without a local write having happened.
     */
    remoteRevision: Int = 0,
    now: () -> Long = { 0L },
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(Tab.Ledgers) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    // Bumped after a write so the screens re-read the log.
    var localRevision by remember { mutableStateOf(0) }
    // A remote pull is a write this device did not make; it has to repaint
    // the same way. The pair is what every read below keys on.
    val revision = Pair(localRevision, remoteRevision)

    // One period for the whole app. Pull up August on Ledgers, switch to
    // Activity, and it is still August — otherwise it reads as two apps that
    // happen to share data. Opens on the current month, which is the grain a
    // budget is defined at.
    var period by remember { mutableStateOf<Period?>(YearMonth.of(today)) }
    // Asked for the period explicitly: without one `ledgers()` totals all of
    // history while the card claims a month.
    val ledgers = remember(revision, period) { repository.ledgers(period) }
    var activityFilter by remember { mutableStateOf(ActivityFilter(currency = null)) }
    val dataRange = remember(revision) { repository.activityDayRange() }
    val days = remember(revision, period, activityFilter) {
        repository.byDay(
            period = period,
            currency = activityFilter.currency,
            needingCategory = activityFilter.needingCategory,
        )
    }
    val needingCategory = remember(revision, period) {
        repository.needingCategoryCount(period)
    }
    val owed = remember(revision) { repository.owed() }
    val habitOn = remember(revision) { repository.gamificationEnabled() }
    val companion = remember(revision) {
        CompanionAnimal.ofCode(repository.choice(Choice.COMPANION))
    }
    // Resolved the same way AppRoot resolves it, so the Settings preview and
    // the app it is previewing cannot disagree about which side they are on.
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val appearanceChoice = remember(revision, systemDark) {
        Appearance.migrated(
            stored = repository.choice(Choice.APPEARANCE),
            storedPaletteIsDark = ThemeChoice.isDarkCode(repository.choice(Choice.THEME)),
        )
    }
    val dark = appearanceChoice.isDark(systemDark)
    val theme = remember(revision, dark) {
        ThemeChoice.ofCode(
            repository.choice(if (dark) Choice.THEME_DARK else Choice.THEME),
            dark = dark,
        )
    }
    val accent = remember(revision) { AccentChoice.ofCode(repository.choice(Choice.ACCENT)) }
    val reminder = remember(revision) {
        ie.shoonya.vitt.notify.Reminder.ofCode(repository.choice(Choice.REMINDER))
    }
    val swipeCards = remember(revision) { repository.choice(Choice.HOME_LAYOUT) != Choice.HOME_STACK }
    // Stored as thousandths of the screen in each axis, so the same value means
    // the same place on a phone and a tablet.
    val companionHome = remember(revision) {
        repository.choice(Choice.COMPANION_HOME)
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.takeIf { it.size == 2 }
            ?.let { Offset(it[0] / 1000f, it[1] / 1000f) }
            ?: DEFAULT_COMPANION_HOME
    }
    // Zero when the layer is off, so nothing downstream can key off it — rather
    // than computing it and trusting every screen to ignore it.
    val recordedDays = remember(revision, habitOn) {
        if (habitOn) repository.recordedDays(today) else emptySet()
    }
    val recorded = recordedDays.size
    val longestRun = remember(revision, habitOn) { if (habitOn) repository.longestRun() else 0 }
    val exporter = rememberFileExporter()
    // Which currency Reports is showing. Null means "the first one there is",
    // resolved at open time — pinning a currency before any exists would leave
    // the screen showing EUR to someone whose ledger is entirely rupees.
    var reportCurrency by remember { mutableStateOf<Currency?>(null) }
    // Mood from the count of budgets on track, which §5.4 permits because it is
    // an outcome rather than an action: it moves over days and cannot be farmed.
    val mood = remember(ledgers) {
        val budgeted = ledgers.filter { it.budget != null }
        Mood.of(onTrack = budgeted.count { (it.remaining()?.minor ?: 0L) >= 0L }, total = budgeted.size)
    }
    // What the companion may point at. Snoozed kinds are ones she already
    // stood by this session and gave up on: asking again in the same sitting
    // is nagging, and the list is recomputed fresh next launch anyway.
    val snoozed = remember { mutableStateMapOf<NudgeKind, Long>() }
    val anchors = rememberNudgeAnchors()
    val nudge: Nudge? = remember(revision, habitOn, syncStatus is SyncStatus.Off, snoozed.size) {
        val all = repository.transactions()
        Nudges.pending(
            transactions = all,
            budgets = repository.budgets(),
            today = today,
            habitOn = habitOn,
            connected = syncStatus !is SyncStatus.Off,
            firstDay = all.minOfOrNull { it.day },
            noSpendDays = repository.noSpendDays(),
        ).firstOrNull { it.kind !in snoozed }
    }
    val nudgeAnchorKey = nudge?.let {
        when (it.kind) {
            NudgeKind.REVIEW_CATEGORIES -> Anchor.TAB_ACTIVITY
            NudgeKind.SETTLE_SPLIT -> Anchor.TAB_PEOPLE
            NudgeKind.SET_BUDGET -> Anchor.ledgerCard(it.currency!!.code)
            NudgeKind.RECORD_TODAY -> Anchor.ADD
            NudgeKind.CONNECT_SHEET -> Anchor.SETTINGS
        }
    }
    // A target that is not on screen (a card scrolled away, another tab open)
    // is no target: she wanders instead of walking to where it would be.
    val nudgeTarget = nudgeAnchorKey?.let { anchors[it] }?.takeIf { sheet == null }

    val accounts = remember(revision) { repository.accounts() }
    // Archived ones included: the Accounts section is the only place they can be
    // unarchived from, and it folds them away itself. Every other consumer here
    // uses `accounts`, which stays live-only.
    val balances = remember(revision) { repository.accountBalances(includeArchived = true) }
    val transfers = remember(revision) { repository.transfers() }
    val participants = remember(revision) { repository.openSplitParticipants() }
    val indexOf: (Currency) -> Int = { c -> ledgers.firstOrNull { it.currency == c }?.index ?: 0 }
    // Resolved against `balances` rather than `accounts`, so a transfer into an
    // account that was later archived still names it. Archiving is not deleting,
    // and a history that reads "unknown account" would suggest otherwise.
    val nameOf: (String) -> String = { id ->
        balances.firstOrNull { it.account.id == id }?.account?.name ?: "unknown account"
    }
    // Days-since-epoch is all the model carries, and a real date formatter is
    // platform work. Relative wording is honest and needs no locale.
    val formatDay: (Int) -> String = { day ->
        when (val ago = today - day) {
            0 -> "today"
            1 -> "yesterday"
            in 2..30 -> "$ago days ago"
            else -> "day $day"
        }
    }

    // Bumped by any touch anywhere. The companion's strip watches this and
    // halts mid-step, which is the design's "any touch stops her and she looks
    // up" rule. Observed on the Initial pass so it never consumes the gesture:
    // a pet that ate a tap would be a bug, not a character.
    var interactions by remember { mutableStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // The import in progress. The raw text is kept so that changing the account
    // re-reads the file: a minor unit is not a fixed thing, and "12.50" scaled
    // for euro is meaningless in yen, which has no decimals at all.
    val picker = ie.shoonya.vitt.ui.platform.rememberFilePicker()
    var importText by remember { mutableStateOf<String?>(null) }
    var importBusy by remember { mutableStateOf(false) }
    var importAccountId by remember { mutableStateOf<String?>(null) }
    var importOrder by remember { mutableStateOf<ie.shoonya.vitt.capture.CsvDate.Order?>(null) }

    val importPlan = remember(importText, importAccountId, importOrder, accounts) {
        val text = importText ?: return@remember null
        val currency = accounts.firstOrNull { it.id == importAccountId }?.currency
            ?: accounts.firstOrNull()?.currency
            ?: Currency.EUR
        val parsed = ie.shoonya.vitt.capture.CsvImport.parse(text, currency)
        // The file's own answer unless the user has overridden it, which only
        // happens when the file could not answer for itself.
        val order = importOrder
            ?: ie.shoonya.vitt.capture.CsvDate.order(parsed.rows.map { it.date })
        ie.shoonya.vitt.capture.CsvPlan.of(parsed, order)
    }

    // What the companion is expressing, from what is actually true.
    //
    // `justSaved` is the moment rather than the day: PERKED lasts until
    // midnight, and a glad face that sat there all evening would be a
    // decoration rather than a reaction. Cleared by the next interaction.
    var justSaved by remember { mutableStateOf(false) }
    val liveliness = remember(revision, today) {
        ie.shoonya.vitt.model.Liveliness.of(
            repository.transactions().maxOfOrNull { it.day }?.let { today - it },
        )
    }
    val companionFace = remember(liveliness, justSaved, needingCategory, ledgers) {
        ie.shoonya.vitt.model.CompanionFace.of(
            liveliness = liveliness,
            justSaved = justSaved,
            needsAttention = needingCategory,
            // A pocket with nothing left in it. A fact about the pocket, and
            // the only negative signal the companion carries at all.
            anyBudgetSpent = ledgers.any { l -> l.remaining()?.let { it.minor <= 0L } == true },
        )
    }

    // Republished whenever the figures move, so the home screen is never
    // showing yesterday's number. Keyed on the ledgers themselves rather than
    // on the revision counter: a theme change bumps the revision and must not
    // cost a write.
    LaunchedEffect(ledgers, recorded) {
        ie.shoonya.vitt.widget.publishWidgetSnapshot(
            ie.shoonya.vitt.widget.WidgetSnapshot.from(
                ledgers = ledgers,
                // The currency of the account last used, which is the one the
                // person is most likely thinking in.
                preferred = accounts
                    .firstOrNull { it.id == repository.choice(Choice.LAST_ACCOUNT) }
                    ?.currency,
                recordedDays = recorded,
                // The widget divides by this at every midnight, so it can
                // recompute without the app ever running.
                monthEndDay = ie.shoonya.vitt.time.YearMonth.of(today).lastDay,
            )?.encode(),
        )
    }

    // Shared text arriving while the app is open, or already waiting when it
    // is launched by a share. Held for the duration of the sheet so a
    // recomposition does not drop the prefill the user is halfway through
    // correcting, and cleared on close so the next manual + opens blank.
    var capturePrefill by remember {
        mutableStateOf<ie.shoonya.vitt.capture.ParsedTransaction?>(null)
    }
    // Set only by the People tab's empty state, and cleared the moment the Add
    // sheet closes: reaching the sheet from anywhere else must not inherit
    // somebody's earlier intention to split.
    var splitFromPeople by remember { mutableStateOf(false) }
    /** Why the last Save was refused. Cleared whenever the sheet opens or closes. */
    var saveError by remember { mutableStateOf<String?>(null) }
    // Skips the initial composition: a counter starting at zero must not open
    // a sheet on every launch.
    LaunchedEffect(openAddTick) {
        if (openAddTick > 0) {
            capturePrefill = null; splitFromPeople = false; saveError = null
            sheet = Sheet.Add
        }
    }
    LaunchedEffect(pendingCapture) {
        pendingCapture?.let {
            capturePrefill = it
            sheet = Sheet.Add
            onCaptureConsumed()
        }
    }

    // Shown once, on a cold install, before the shell exists.
    //
    // It lives here rather than in `AppRoot` so that finishing can drop straight
    // into the Add sheet: someone who has just named five accounts is one tap
    // from their first real entry, and that entry now has somewhere to land.
    // Before this screen existed the first entry on an empty app was recorded
    // against no account at all, because the person had none and nothing had
    // told them accounts were the thing the app is built around.
    val setupDone = remember(revision) { repository.choice(Choice.SETUP_DONE) != null }
    if (!setupDone) {
        SetupScreen(
            onDone = { drafts ->
                // Ids up front, so the first one can be pre-selected below
                // without reading the accounts back out of the log.
                val ids = drafts.map { newId() }
                drafts.forEachIndexed { i, draft ->
                    // Kind is left at its default on purpose: it decides only
                    // whether a negative balance reads as "owed" or
                    // "overdrawn", and asking for it up front gives it a weight
                    // it does not carry. The opening balance *is* asked for, in
                    // its own optional step, and a draft that skipped it opens
                    // at zero exactly as before.
                    repository.openAccount(
                        ids[i],
                        draft.name,
                        draft.currency,
                        opening = draft.opening ?: Money(0, draft.currency),
                    )
                }
                // Most people pay from one account most of the time, and the
                // one they named first is the likeliest. Saves a tap on the
                // very first entry, which is the one that most needs to be easy.
                ids.firstOrNull()?.let { repository.setChoice(Choice.LAST_ACCOUNT, it) }
                repository.setChoice(Choice.SETUP_DONE, Choice.SETUP_YES)
                localRevision++
                sheet = Sheet.Add
            },
            onSkip = {
                // Marked done even though nothing was named: skipping is an
                // answer, and asking again on the next launch would make it a
                // postponement the person never agreed to.
                repository.setChoice(Choice.SETUP_DONE, Choice.SETUP_YES)
                localRevision++
            },
            modifier = modifier.fillMaxSize().background(Vitt.colors.ground),
        )
        return
    }

    CompositionLocalProvider(LocalNudgeAnchors provides anchors) {
    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Vitt.colors.ground)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed }) {
                            interactions++
                            // The glad face belongs to the save, not to the
                            // rest of the evening.
                            justSaved = false
                        }
                    }
                }
            },
    ) {
        // The fade is on the host rather than on each screen, so every tab
        // gets the same edge and a new one cannot forget it.
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .edgeFade(Vitt.colors.ground),
        ) {
            when (tab) {
                Tab.Ledgers -> LedgersScreen(
                    ledgers = ledgers,
                    owed = owed,
                    daysRecorded = recorded,
                    onSetBudget = { sheet = Sheet.SetBudget(it) },
                    onOpenSettings = { sheet = Sheet.Settings },
                    onOpenHabit = if (habitOn) ({ sheet = Sheet.Habit }) else null,
                    swipeCards = swipeCards,
                    period = period,
                    dataRange = dataRange,
                    today = today,
                    onPeriodChange = { period = it },
                    onPickGrain = { sheet = Sheet.PickGrain },
                    balances = balances,
                    transfers = transfers,
                    onAddAccount = { sheet = Sheet.NewAccount },
            onImport = { sheet = Sheet.Import },
                    onEditAccount = { sheet = Sheet.EditAccount(it) },
                    onTransfer = { sheet = Sheet.Transfer },
                    accountName = nameOf,
                    formatDay = formatDay,
                    // Keep the list clear of her band while she is standing
                    // where the app put her. `Pip.kt` is explicit that the
                    // "never over your numbers" rule exists to stop *the app*
                    // putting a pet in the user's way, and at six currencies
                    // the cards run the full height of the screen — so the
                    // default home landed her squarely on a budget line and its
                    // health bar, with the user having done nothing. Once she
                    // has been dragged somewhere the reservation goes away and
                    // the 60dp comes back: a pet the user placed on their own
                    // numbers is their business.
                    companionInset = if (
                        habitOn && companion != null &&
                        companionHome == DEFAULT_COMPANION_HOME
                    ) 64.dp else 0.dp,
                )
                Tab.Activity -> ActivityScreen(
                    companionInset = if (habitOn && companion != null) 64.dp else 0.dp,
                    days = days,
                    currencyIndex = indexOf,
                    onEdit = { sheet = Sheet.EditCategory(it.id) },
                    filter = activityFilter,
                    period = period,
                    dataRange = dataRange,
                    today = today,
                    currencies = ledgers.map { it.currency },
                    needingCategory = needingCategory,
                    onFilterChange = { activityFilter = it },
                    onPeriodChange = { period = it },
                    onPickGrain = { sheet = Sheet.PickGrain },
                )
                Tab.People -> PeopleScreen(
                    participants = participants,
                    outstandingFor = { repository.outstandingBy(it) },
                    splitsFor = { who ->
                        repository.openSplits().filter { who in it.splitWith }
                    },
                    onShare = { who ->
                        SettlementSummary.forParticipant(
                            participant = who,
                            splits = repository.transactions(),
                            formatDay = formatDay,
                        )?.let { sheet = Sheet.Summary(it.subject, it.body) }
                    },
                    onOpenSplit = { sheet = Sheet.Split(it.id) },
                    onSplitSomething = { splitFromPeople = true; sheet = Sheet.Add },
                    companionInset = if (habitOn && companion != null) 64.dp else 0.dp,
                    formatDay = formatDay,
                )
                Tab.Reports -> {
                    val currency = reportCurrency
                        ?: ledgers.firstOrNull()?.currency
                        ?: Currency.EUR
                    val ledger = ledgers.firstOrNull { it.currency == currency }
                    // One fold of the log for all three charts, rather than one
                    // each — `Insights` takes a transaction list precisely so a
                    // screen with three sections is not three passes.
                    val all = remember(revision) { repository.transactions() }
                    val month = period as? YearMonth
                    ReportsSheet(
                        currency = currency,
                        currencies = ledgers.map { it.currency },
                        currencyIndex = indexOf,
                        onCurrencyChange = { reportCurrency = it },
                        period = period,
                        today = today,
                        spent = ledger?.spent ?: ie.shoonya.vitt.money.Money(0, currency),
                        received = ledger?.received ?: ie.shoonya.vitt.money.Money(0, currency),
                        budget = ledger?.budget,
                        comparison = remember(revision, currency, period) {
                            Insights.compare(all, currency, period)
                        },
                        projection = remember(revision, currency, period, today) {
                            (period as? YearMonth)?.let {
                                Insights.projectMonth(all, currency, it, today)
                            }
                        },
                        cumulative = remember(revision, currency, period, today) {
                            month?.let { Insights.dailyCumulative(all, currency, it, today) } ?: emptyList()
                        },
                        daysInMonth = month?.let { Civil.daysInMonth(it.year, it.month) } ?: 0,
                        weekday = remember(revision, currency, period) {
                            Insights.byWeekday(all, currency, period)
                        },
                        merchantsIn = { category ->
                            Insights.merchantsIn(all, currency, period, category)
                        },
                        // All-time has no grain to step along, so it gets no
                        // trend rather than a single bar labelled "All time".
                        trend = remember(revision, currency, period) {
                            period?.let { Insights.trend(all, currency, it, count = 6) }
                                ?: emptyList()
                        },
                        categories = remember(revision, currency, period) {
                            Insights.byCategory(all, currency, period)
                        },
                        merchants = remember(revision, currency, period) {
                            Insights.topMerchants(all, currency, period, limit = 5)
                        },
                        onImport = { sheet = Sheet.Import },
                        onExport = {
                            // Two files, because a transfer has two amounts and
                            // two accounts and would leave half of every
                            // transaction row blank. Offered in one call: two
                            // calls lose the second file silently on iOS.
                            exporter.offer(
                                buildList {
                                    add(
                                        ExportFile(
                                            "vitt-transactions.csv",
                                            repository.exportTransactionsCsv(),
                                        ),
                                    )
                                    // Omitted rather than exported empty, so a
                                    // user who has never moved money between
                                    // accounts is not handed a header row to
                                    // wonder about.
                                    if (transfers.isNotEmpty()) {
                                        add(
                                            ExportFile(
                                                "vitt-transfers.csv",
                                                repository.exportTransfersCsv(),
                                            ),
                                        )
                                    }
                                },
                            )
                        },
                        onDone = null,
                    )
                }
            }
        }

        // Her strip: a fixed band that is never over a number or a control, and
        // has nothing interactive behind it. Hidden with the habit layer, since
        // a wandering pet is the loudest thing the layer does.
        TabBar(
            current = tab,
            onSelect = { tab = it },
            onAdd = { sheet = Sheet.Add },
            rightTabs = listOf(Tab.People, Tab.Reports),
        )
    }

    // An overlay, so she consumes no layout and can be anywhere the user drops
    // her. On the home screen only: a pet over a dense transaction list
    // competes with the scanning that list exists for.
    if (habitOn && companion != null && tab == Tab.Ledgers) {
        CompanionLayer(
            daysRecorded = recorded,
            currencyCount = ledgers.size,
            animal = companion,
            mood = mood,
            home = companionHome,
            onHomeChange = {
                repository.setChoice(
                    Choice.COMPANION_HOME,
                    "${(it.x * 1000).toInt()},${(it.y * 1000).toInt()}",
                )
                localRevision++
            },
            interactionTick = interactions,
            intent = companionFace,
            // The subject of her own face, resolved to where it is on screen.
            // Null sends her home, which is the honest answer to a quiet one.
            intentTarget = when (companionFace.whereTo()) {
                ie.shoonya.vitt.model.CompanionWhere.ACTIVITY -> anchors[Anchor.TAB_ACTIVITY]
                ie.shoonya.vitt.model.CompanionWhere.ADD -> anchors[Anchor.ADD]
                ie.shoonya.vitt.model.CompanionWhere.SPENT_LEDGER ->
                    ledgers.firstOrNull { l -> l.remaining()?.let { it.minor <= 0L } == true }
                        ?.let { anchors[Anchor.ledgerCard(it.currency.code)] }
                // Home and stay-put both mean "do not travel".
                else -> null
            },
            nudge = nudge,
            nudgeTarget = nudgeTarget,
            onNudgeTap = { n ->
                snoozed[n.kind] = 1L
                when (n.kind) {
                    NudgeKind.REVIEW_CATEGORIES -> {
                        activityFilter = activityFilter.copy(needingCategory = true)
                        tab = Tab.Activity
                    }
                    NudgeKind.SETTLE_SPLIT -> tab = Tab.People
                    NudgeKind.SET_BUDGET -> sheet = Sheet.SetBudget(n.currency!!)
                    NudgeKind.RECORD_TODAY -> sheet = Sheet.Add
                    NudgeKind.CONNECT_SHEET -> sheet = Sheet.Settings
                }
            },
            onNudgeExpired = { n -> snoozed[n.kind] = 1L },
        )
    }
    }
    }

    sheet?.let { open ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = sheetState,
            containerColor = Vitt.colors.ground,
            // The two tall sheets scroll inside themselves, and a scrolling
            // list inside a draggable sheet is two gestures fighting over one
            // finger: a pull that should scroll back up would fling the sheet
            // shut instead. They close from Done or the scrim. The short
            // sheets keep the pull, which is right for a one-screen form.
            sheetGesturesEnabled = open !is Sheet.Settings,
            // No handle on a sheet that cannot be pulled: the dash is a promise
            // of a gesture, and Settings closes from Done or the scrim.
            dragHandle = if (open is Sheet.Settings) null else {
                { androidx.compose.material3.BottomSheetDefaults.DragHandle() }
            },
        ) {
            // A swipe in from the left edge closes the sheet, the way it pops a
            // screen everywhere else on the platform. Edge only, and only a
            // deliberate distance: the keypad, the chips and the pager all
            // live in these sheets and a stray horizontal drag on any of them
            // must not throw the sheet away.
            Box(
                Modifier.fillMaxSize().pointerInput(open) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.position.x > EDGE_SWIPE_ZONE.toPx()) return@awaitEachGesture
                        val startX = down.position.x
                        var travelled = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            travelled = change.position.x - startX
                            if (travelled > EDGE_SWIPE_DISTANCE.toPx()) {
                                change.consume()
                                sheet = null
                                break
                            }
                        }
                    }
                },
            ) {
            when (open) {
                Sheet.Add -> AddScreen(
                    prefill = capturePrefill,
                    startSplit = splitFromPeople,
                    // The currencies actually held, and every currency the app
                    // knows only when none are.
                    //
                    // Offering all six to somebody with a euro and a rupee
                    // account is four chips of noise on the screen they use
                    // most, and it invites logging into a currency no account
                    // can hold. The fallback stays because a fresh install has
                    // no accounts yet, and a person in Dubai or Tokyo has to be
                    // able to log their first entry in their own money.
                    currencies = accounts.map { it.currency }.distinct()
                        .ifEmpty { (ledgers.map { it.currency } + Currency.entries).distinct() },
                    accounts = accounts,
                    frequentSpending = remember(revision) {
                        repository.frequentCategories(today, spending = true)
                    },
                    frequentIncome = remember(revision) {
                        repository.frequentCategories(today, spending = false)
                    },
                    lastAccountId = remember(revision) { repository.choice(Choice.LAST_ACCOUNT) },
                    error = saveError,
                    onSave = { new ->
                        val id = newId()
                        // record() refuses anything it cannot write — a zero, a
                        // split whose currencies disagree, an account in
                        // another currency — and it refuses by throwing. The
                        // sheet is the one place that must never let that go
                        // past: an uncaught throw here takes the app down with
                        // the figure still untyped-in, and a caught-and-ignored
                        // one closes the sheet on a transaction that was never
                        // written. Both read to the person as "I saved it and
                        // it is not there", which is the single failure a money
                        // app does not get to have.
                        //
                        // Every one of these is a bug rather than a user error,
                        // so the wording does not pretend otherwise. What it
                        // does do is keep the sheet open with the amount in it,
                        // so nothing has to be typed twice.
                        val failure = try {
                            repository.record(
                                id = id,
                                amount = new.amount,
                                day = today,
                                category = new.category?.code,
                                accountId = new.accountId,
                                totalPaid = new.totalPaid,
                                note = new.note,
                            )
                            null
                        } catch (e: IllegalArgumentException) {
                            e.message ?: "could not be saved"
                        }
                        if (failure != null) {
                            saveError = "Not saved: $failure. Nothing was lost. " +
                                "The amount is still here."
                            return@AddScreen
                        }
                        saveError = null
                        // Remembered so the next add starts on the same account.
                        // Only on a change: a choice event per entry would put a
                        // row in the sheet for every coffee.
                        new.accountId?.let {
                            if (repository.choice(Choice.LAST_ACCOUNT) != it) {
                                repository.setChoice(Choice.LAST_ACCOUNT, it)
                            }
                        }
                        localRevision++
                        // A split is not finished when it is saved: who was in
                        // on it is still unsaid, and the only place to say it
                        // was a row tap nobody was told about. Open it now.
                        capturePrefill = null; splitFromPeople = false; saveError = null
                        // The reward, spent immediately. Nothing accumulates,
                        // so nothing can be lost and there is no streak to
                        // protect by avoiding the app.
                        justSaved = true
                        sheet = if (new.totalPaid != null) Sheet.Split(id) else null
                    },
                    onCancel = { capturePrefill = null; splitFromPeople = false; saveError = null; sheet = null },
                )

                Sheet.NewAccount -> AccountSheet(
                    heldCurrencies = accounts.map { it.currency }.distinct(),
                    onCreate = { name, currency, kind, opening ->
                        repository.openAccount(newId(), name, currency, kind, opening)
                        localRevision++
                        sheet = null
                    },
                    onCancel = { sheet = null },
                )

                is Sheet.EditAccount -> {
                    // Re-read on every revision, like the other edit sheets, so
                    // an archive toggled inside the sheet is reflected by the
                    // sheet's own button without closing it.
                    val account = remember(revision, open.id) {
                        repository.accounts(includeArchived = true).firstOrNull { it.id == open.id }
                    }
                    if (account == null) {
                        sheet = null
                    } else {
                        EditAccountSheet(
                            account = account,
                            onSave = { name, kind, opening ->
                                // One event per changed field, and none for a
                                // field left alone: an unchanged name should not
                                // put a row in the user's spreadsheet.
                                if (name != account.name) repository.renameAccount(open.id, name)
                                if (kind != account.kind) repository.setAccountKind(open.id, kind)
                                if (opening != account.opening) {
                                    repository.setAccountOpening(open.id, opening)
                                }
                                localRevision++
                                sheet = null
                            },
                            onArchivedChange = {
                                repository.setAccountArchived(open.id, it)
                                localRevision++
                            },
                            onCancel = { sheet = null },
                        )
                    }
                }

                Sheet.Import -> ImportSheet(
                    plan = importPlan,
                    accounts = accounts,
                    accountId = importAccountId ?: accounts.firstOrNull()?.id,
                    onAccountChange = { importAccountId = it },
                    busy = importBusy,
                    onPickFile = {
                        importBusy = true
                        picker.pick { text ->
                            importBusy = false
                            // A cancelled pick leaves the sheet exactly as it
                            // was, which is what cancelling should do.
                            if (text != null) {
                                importText = text
                                importOrder = null
                            }
                        }
                    },
                    onDateOrder = { importOrder = it },
                    onImport = { accountId, rows ->
                        val account = accounts.firstOrNull { it.id == accountId }
                        if (account != null) {
                            rows.forEach { row ->
                                val amount = row.amount ?: return@forEach
                                val day = row.day ?: return@forEach
                                repository.record(
                                    id = newId(),
                                    amount = amount,
                                    day = day,
                                    merchant = row.merchant,
                                    accountId = accountId,
                                    // Marked at the only place that knows. The
                                    // habit count leaves these days out; nothing
                                    // else treats them differently.
                                    imported = true,
                                )
                            }
                            repository.setChoice(Choice.LAST_ACCOUNT, accountId)
                            localRevision++
                        }
                        importText = null
                        importOrder = null
                        sheet = null
                    },
                    onCancel = {
                        importText = null
                        importOrder = null
                        sheet = null
                    },
                )

                Sheet.Transfer -> TransferSheet(
                    accounts = accounts,
                    onTransfer = { from, to, sent, received ->
                        repository.transfer(
                            id = newId(),
                            fromAccountId = from.id,
                            toAccountId = to.id,
                            sent = sent,
                            received = received,
                            day = today,
                        )
                        localRevision++
                        sheet = null
                    },
                    onCancel = { sheet = null },
                )

                is Sheet.Split -> {
                    // Re-read on every revision so the figures update in place
                    // as participants are added and repayments recorded.
                    val split = remember(revision, open.id) {
                        repository.transactions().firstOrNull { it.id == open.id }
                    }
                    if (split == null) {
                        sheet = null
                    } else {
                        SplitSheet(
                            split = split,
                            onAddParticipant = {
                                repository.addSplitParticipant(open.id, it); localRevision++
                            },
                            onRemoveParticipant = {
                                repository.removeSplitParticipant(open.id, it); localRevision++
                            },
                            onSettle = { repository.settle(open.id, it); localRevision++ },
                            onSettleInFull = {
                                repository.settleInFull(open.id); localRevision++; sheet = null
                            },
                            onDone = { sheet = null },
                        )
                    }
                }

                is Sheet.EditCategory -> {
                    val txn = remember(revision, open.id) {
                        repository.transactions().firstOrNull { it.id == open.id }
                    }
                    if (txn == null) {
                        sheet = null
                    } else {
                        CategorySheet(
                            transaction = txn,
                            pastCount = remember(revision, open.id) {
                                repository.pastMatching(open.id).size
                            },
                            onPick = { category, teach, applyToPast ->
                                repository.categorise(
                                    open.id,
                                    category,
                                    teach = teach,
                                    applyToPast = applyToPast,
                                )
                                localRevision++
                                sheet = null
                            },
                            onNoteChange = { repository.setNote(open.id, it); localRevision++ },
                            onDone = { sheet = null },
                        )
                    }
                }

                Sheet.PickGrain -> GrainSheet(
                    period = period,
                    today = today,
                    onPick = {
                        period = it
                        sheet = null
                    },
                )

                Sheet.Habit -> HabitScreen(
                    face = companionFace,
                    daysRecorded = recorded,
                    recordedDays = recordedDays,
                    longestRun = longestRun,
                    today = today,
                    windowDays = 30,
                    currencyCount = ledgers.size,
                    animal = companion,
                    lastRecorded = remember(revision) { repository.lastRecordedByCurrency() },
                    onNothingToday = if (today in recordedDays) null else ({
                        repository.markNothingSpent(today)
                        localRevision++
                    }),
                    months = remember(revision) { repository.recordedDaysPerMonth(today) },
                    currencyIndex = indexOf,
                    onDone = { sheet = null },
                )

                Sheet.Settings -> SettingsSheet(
                    companion = companion,
                    onCompanionChange = {
                        // "None" is stored as a value, not by clearing the
                        // choice: cleared means unset, which means the default,
                        // which would put the pig back.
                        repository.setChoice(
                            Choice.COMPANION,
                            it?.code ?: CompanionAnimal.NONE,
                        )
                        localRevision++
                    },
                    theme = theme,
                    onThemeChange = {
                        // Written to the key for the side it belongs to, so the
                        // other side's palette is left where the user put it.
                        repository.setChoice(
                            if (it.dark) Choice.THEME_DARK else Choice.THEME,
                            it.code,
                        )
                        localRevision++
                        onAppearanceChange()
                    },
                    appearance = appearanceChoice,
                    onAppearanceChange = {
                        repository.setChoice(Choice.APPEARANCE, it.code)
                        localRevision++
                        onAppearanceChange()
                    },
                    accent = accent,
                    onAccentChange = {
                        repository.setChoice(Choice.ACCENT, it.code)
                        localRevision++
                        onAppearanceChange()
                    },
                    currencyCount = ledgers.size,
                    reminder = reminder,
                    onReminderChange = { picked ->
                        scope.launch {
                            // The branches live in :shared and are tested
                            // there, including the refusal path. A branch
                            // inside a lambda inside a bottom sheet is a
                            // branch nothing can reach.
                            val stored = ie.shoonya.vitt.notify.applyReminderChoice(
                                picked = picked,
                                scheduler = ie.shoonya.vitt.notify.platformReminderScheduler,
                                setChoice = { repository.setChoice(Choice.REMINDER, it) },
                                clearChoice = { repository.clearChoice(Choice.REMINDER) },
                            )
                            if (stored) localRevision++
                        }
                    },
                    swipeCards = swipeCards,
                    onSwipeCardsChange = {
                        repository.setChoice(Choice.HOME_LAYOUT, if (it) Choice.HOME_SWIPE else Choice.HOME_STACK)
                        localRevision++
                    },
                    syncStatus = syncStatus,
                    derivedTabs = derivedTabs,
                    sheetActions = sheetActions,
                    now = now,
                    gamificationEnabled = habitOn,
                    onGamificationChange = {
                        repository.setGamificationEnabled(it)
                        localRevision++
                        // Turning the habit off closes its sheet's route with it.
                        if (!it && sheet == Sheet.Habit) sheet = null
                    },
                    onDone = { sheet = null },
                )

                is Sheet.SetBudget -> BudgetSheet(
                    currency = open.currency,
                    existing = remember(revision, open.currency) {
                        repository.budgets()[open.currency]?.limit
                    },
                    onSet = {
                        repository.setBudget(open.currency, it)
                        localRevision++
                        sheet = null
                    },
                    onClear = {
                        repository.clearBudget(open.currency)
                        localRevision++
                        sheet = null
                    },
                    onCancel = { sheet = null },
                )

                is Sheet.Summary -> SummarySheet(
                    subject = open.subject,
                    body = open.body,
                    onDone = { sheet = null },
                )
            }
            }
        }
    }
}

/**
 * The settlement text, shown rather than sent.
 *
 * There is no server, so there is no push notification — and asking for
 * `gmail.send` would make the scope set sensitive and trigger CASA, verification
 * and the 100-user cap. Handing the text to the platform's own share sheet costs
 * nothing, so this screen is the seam where that will plug in.
 */
@Composable
private fun SummarySheet(subject: String, body: String, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Summary", style = Vitt.type.title, color = Vitt.colors.ink)
            TextButton(onClick = onDone) { Text("Done") }
        }
        Text(subject, style = Vitt.type.body, color = Vitt.colors.ink)
        Text(body, style = Vitt.type.label, color = Vitt.colors.inkMuted)
        Text(
            // Kept, because the plain form looks like a mistake otherwise. Cut
            // to one line: the reasoning belongs here, not on the screen.
            "Written plainly, since 1.234 means different numbers in different places.",
            style = Vitt.type.label,
            color = Vitt.colors.inkFaint,
        )
    }
}

@Composable
private fun TabBar(
    current: Tab,
    onSelect: (Tab) -> Unit,
    onAdd: () -> Unit,
    rightTabs: List<Tab>,
) {
    val colors = Vitt.colors
    // No fill behind the tab bar. The design's light tab row (`.ltab`) sits
    // directly on the ground with generous bottom padding; a filled bar reads as
    // a heavier, more institutional app than this is meant to be.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 13.dp, bottom = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(Tab.Ledgers, Tab.Activity).forEach {
            TabItem(it, current, onSelect, Modifier.weight(1f).anchorFor(it))
        }

        // The centre action. Deliberately larger and not labelled as a tab.
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Vitt.space.tight),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .nudgeAnchor(Anchor.ADD)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .clickable(onClick = onAdd)
                    // Without this the primary action of the whole app announces
                    // itself as "plus".
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Add transaction"
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "+",
                    style = Vitt.type.title,
                    color = colors.ground,
                    // The name is on the Box; without clearing this the button
                    // announces itself as "Add transaction, plus".
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }

        rightTabs.forEach {
            TabItem(it, current, onSelect, Modifier.weight(1f).anchorFor(it))
        }
        // Keeps the bar symmetrical when Habit is gone: without a filler the
        // centre button slides off centre, which reads as a layout bug.
        if (rightTabs.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Modifier.anchorFor(tab: Tab): Modifier = when (tab) {
    Tab.Activity -> nudgeAnchor(Anchor.TAB_ACTIVITY)
    Tab.People -> nudgeAnchor(Anchor.TAB_PEOPLE)
    else -> this
}

@Composable
private fun TabItem(tab: Tab, current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier) {
    val selected = tab == current
    val tint = if (selected) Vitt.colors.accent else Vitt.colors.inkMuted
    Column(
        // `selectable` rather than `clickable`: it is what carries "tab" and
        // "selected" to VoiceOver and TalkBack. A clickable tab announces its
        // label and nothing else, so the current tab is indistinguishable from
        // the other four with the screen reader on.
        modifier = modifier
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = { onSelect(tab) },
            )
            .padding(vertical = Vitt.space.hair),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        VittGlyph(tab.icon, tint, Modifier.size(23.dp))
        Text(
            tab.label,
            style = Vitt.type.caption.copy(letterSpacing = 0.2.sp),
            textAlign = TextAlign.Center,
            color = tint,
        )
    }
}

/** How far from the left edge a swipe may start and still count as a back swipe. */
private val EDGE_SWIPE_ZONE = 28.dp

/** How far the finger has to travel before the sheet closes. */
private val EDGE_SWIPE_DISTANCE = 72.dp
