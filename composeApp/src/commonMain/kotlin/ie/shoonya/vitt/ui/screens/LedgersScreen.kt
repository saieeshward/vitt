package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ie.shoonya.vitt.model.Ledger
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.time.periodPhrase
import ie.shoonya.vitt.ui.nudgeAnchor
import ie.shoonya.vitt.ui.theme.Vitt

/**
 * The home screen: one card per currency, each complete on its own.
 *
 * There is no total anywhere, and the line under the last card says so. The
 * absence is stated rather than merely left out, so it reads as rigour instead
 * of a missing feature.
 */
@Composable
fun LedgersScreen(
    ledgers: List<Ledger>,
    owed: Map<Currency, Money>,
    daysRecorded: Int,
    onSetBudget: (Currency) -> Unit,
    onOpenSettings: () -> Unit,
    /** Null hides the icon: the habit switch is off, so there is nothing to open. */
    onOpenHabit: (() -> Unit)?,
    /** One card at a time with a swipe, or all of them stacked. The user's call. */
    swipeCards: Boolean,
    period: ie.shoonya.vitt.time.Period?,
    dataRange: IntRange?,
    today: Int,
    onPeriodChange: (ie.shoonya.vitt.time.Period?) -> Unit,
    onPickGrain: () -> Unit,
    balances: List<ie.shoonya.vitt.model.AccountBalance>,
    transfers: List<ie.shoonya.vitt.model.Transfer>,
    onAddAccount: () -> Unit,
    /** Offered from the empty state, where a person with history is most likely to want it. */
    onImport: () -> Unit,
    /** Opens Add, for the first-day welcome card. */
    onAdd: () -> Unit = {},
    /** Today's companion, or null when the habit layer is off. */
    companion: ie.shoonya.vitt.ui.CompanionAnimal? = null,
    onEditAccount: (String) -> Unit,
    onTransfer: () -> Unit,
    accountName: (String) -> String,
    formatDay: (Int) -> String,
    /** Room kept at the foot of the list for the companion's default band. */
    companionInset: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    // Read here, outside the list builder, which is not composable. As many
    // cards across as fit at a width a large rupee balance still reads at
    // without wrapping, rather than by width class: a phone on its side is
    // "expanded" by width and cramped by three.
    val window = ie.shoonya.vitt.ui.LocalWindowLayout.current
    val across = if (window.width == ie.shoonya.vitt.layout.WindowLayout.Width.COMPACT) 1
    else ((minOf(window.widthDp, WIDE_CONTENT_DP) - RAIL_DP) / MIN_CARD_DP).coerceIn(1, 3)
    // Folded by default, per visit. Not persisted: "show all" is a look, not a
    // preference, and the folded list is what keeps the home one screen.
    var showAllAccounts by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showArchived by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showAllTransfers by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    LazyColumn(
        // The inset shortens the *viewport*, not the content. As bottom
        // `contentPadding` it only cleared her at the very foot of the list —
        // which is the one scroll position it was checked at — and anywhere
        // else the list simply scrolled cards under her band regardless.
        modifier = modifier.fillMaxWidth().padding(bottom = companionInset),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Ledgers", style = Vitt.type.display, color = Vitt.colors.ink)
                // Both in the header rather than as tabs: `design-identity.md`
                // puts reports and settings behind Ledgers' header icons because
                // a monthly visit must not slow the daily path.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    onOpenHabit?.let { HeaderIcon(ie.shoonya.vitt.ui.VittIcon.Spark, "Habit", it) }
                    HeaderIcon(
                ie.shoonya.vitt.ui.VittIcon.Gear,
                "Settings",
                onOpenSettings,
                modifier = Modifier.nudgeAnchor(ie.shoonya.vitt.ui.Anchor.SETTINGS),
            )
                }
            }
        }

        item {
            PeriodControl(
                period = period,
                dataRange = dataRange,
                today = today,
                onChange = onPeriodChange,
                onPickGrain = onPickGrain,
            )
        }

        // The companion is no longer an item here. It owns a fixed strip above
        // the tab bar instead, which is what the design's wander artboard
        // specifies and what makes wandering safe: a band outside the scrolling
        // list cannot remeasure the cards below it however far she walks.

        if (ledgers.isEmpty()) {
            item { EmptyLedgers(onImport = onImport, onAdd = onAdd, companion = companion, currencies = balances.map { it.account.currency }.distinct().size) }
        } else if (ledgers.size > 1 && across > 1) {
            // Room for every currency at once: a grid, two across on a medium
            // window and three on a wide one, in assignment order. The swipe
            // choice is a phone's answer to not having the room, and does not
            // apply here (docs/ipad-plan.clan, phase 2).
            items(ledgers.chunked(across)) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Vitt.space.base),
                ) {
                    row.forEach { ledger ->
                        Box(Modifier.weight(1f)) {
                            LedgerCard(
                                ledger = ledger,
                                onSetBudget = onSetBudget,
                                periodPhrase = periodPhrase(period, today),
                                daysLeft = daysLeft(period, today),
                                canSetBudget = period is ie.shoonya.vitt.time.YearMonth,
                            )
                        }
                    }
                    // A short last row keeps its cards the width of the others.
                    repeat(across - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            item { NoTotalNote() }
        } else if (swipeCards && ledgers.size > 1) {
            // One card in view, the next peeking at the edge. At six currencies
            // the stack ran past the fold and put Accounts a screen away; a
            // pager keeps the card whole and the rest of the home in reach.
            item {
                LedgerPager(
                    ledgers = ledgers,
                    onSetBudget = onSetBudget,
                    periodPhrase = periodPhrase(period, today),
                    daysLeft = daysLeft(period, today),
                    canSetBudget = period is ie.shoonya.vitt.time.YearMonth,
                )
            }
            item { NoTotalNote() }
        } else {
            items(ledgers) {
                LedgerCard(
                    ledger = it,
                    onSetBudget = onSetBudget,
                    periodPhrase = periodPhrase(period, today),
                    daysLeft = daysLeft(period, today),
                    // A monthly limit can only be set against a month, so the
                    // invitation only appears where acting on it makes sense.
                    canSetBudget = period is ie.shoonya.vitt.time.YearMonth,
                )
            }
            item { NoTotalNote() }
        }

        if (owed.isNotEmpty()) {
            item { OwedRow(owed) }
        }

        accountsSection(
            balances = balances,
            transfers = transfers,
            onAddAccount = onAddAccount,
            onEditAccount = onEditAccount,
            onTransfer = onTransfer,
            accountName = accountName,
            formatDay = formatDay,
            showAllAccounts = showAllAccounts,
            onToggleAccounts = { showAllAccounts = !showAllAccounts },
            showArchived = showArchived,
            onToggleArchived = { showArchived = !showArchived },
            showAllTransfers = showAllTransfers,
            onToggleTransfers = { showAllTransfers = !showAllTransfers },
        )
    }
}

/**
 * The currency cards as pages.
 *
 * The page a user left is the page they come back to, within a session: the
 * state is remembered against the list of currencies, so adding one does not
 * throw the pager back to the first.
 */
@Composable
private fun LedgerPager(
    ledgers: List<Ledger>,
    onSetBudget: (Currency) -> Unit,
    periodPhrase: String,
    daysLeft: Int?,
    canSetBudget: Boolean,
) {
    val state = rememberPagerState(pageCount = { ledgers.size })
    Column(verticalArrangement = Arrangement.spacedBy(Vitt.space.snug)) {
        HorizontalPager(
            state = state,
            // The next card shows its edge, which is how a person learns there
            // is one without being told.
            contentPadding = PaddingValues(end = 28.dp),
            pageSpacing = Vitt.space.base,
            // Room for the card's shadow, which a tight clip would cut flat.
            modifier = Modifier.fillMaxWidth().padding(vertical = Vitt.space.tight),
        ) { page ->
            // Depth on the move and only on the move: a card turns a little on
            // its vertical axis as it is swiped away, like a page, and sits flat
            // at rest. It encodes nothing, which is the only place depth belongs
            // in a money app; a tilted figure is a misread figure. Off under
            // Reduce Motion.
            val flat = ie.shoonya.vitt.ui.platform.prefersReducedMotion()
            val density = androidx.compose.ui.platform.LocalDensity.current.density
            Box(
                Modifier.graphicsLayer {
                    if (flat) return@graphicsLayer
                    val offset = ((state.currentPage - page) + state.currentPageOffsetFraction).coerceIn(-1f, 1f)
                    rotationY = offset * TURN_DEGREES
                    cameraDistance = 14f * density
                    val shrink = 1f - kotlin.math.abs(offset) * 0.05f
                    scaleX = shrink
                    scaleY = shrink
                },
            ) {
                LedgerCard(
                    ledger = ledgers[page],
                    onSetBudget = onSetBudget,
                    periodPhrase = periodPhrase,
                    daysLeft = daysLeft,
                    canSetBudget = canSetBudget,
                )
            }
        }
        // One dot per currency, in its own hue: the dots are the palette key
        // for the whole app, and the filled one says which card is up.
        Row(
            modifier = Modifier.fillMaxWidth()
                .semantics {
                    contentDescription = "Currency ${state.currentPage + 1} of ${ledgers.size}, " +
                        ledgers[state.currentPage].currency.code
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            ledgers.forEachIndexed { i, ledger ->
                val current = i == state.currentPage
                Box(
                    Modifier
                        .size(if (current) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            Vitt.colors.currency(ledger.index)
                                .copy(alpha = if (current) 1f else 0.35f),
                        ),
                )
            }
        }
    }
}

@Composable
private fun HeaderIcon(
    icon: ie.shoonya.vitt.ui.VittIcon,
    /**
     * Required, not optional. The glyphs are drawn to a `Canvas`, which carries
     * no text of any kind, so a header icon without this is an unnamed button.
     */
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A 44dp target around a 23dp glyph. The glyph alone with 6dp of padding
    // came to 35dp, under Apple's 44pt minimum and Android's 48dp guidance —
    // small enough to miss with a thumb on the move, which is when a header
    // icon actually gets used.
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        ie.shoonya.vitt.ui.VittGlyph(icon, Vitt.colors.inkMuted, Modifier.size(23.dp))
    }
}

@Composable
private fun LedgerCard(
    ledger: Ledger,
    onSetBudget: (Currency) -> Unit,
    periodPhrase: String,
    /** Days of the period still ahead, or null when the period is not the current one. */
    daysLeft: Int?,
    canSetBudget: Boolean,
) {
    val colors = Vitt.colors
    val remaining = ledger.remaining()
    val pressure = ledger.pressure()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .nudgeAnchor(ie.shoonya.vitt.ui.Anchor.ledgerCard(ledger.currency.code))
            // A white card lifted off the cream ground, per the design's own
            // `box-shadow: 0 2px 10px rgba(36,31,51,0.06)`. A card tinted a
            // shade of the background instead reads as muddy, which is what an
            // earlier pass here produced.
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(Vitt.radius.card),
                ambientColor = colors.shadow,
                spotColor = colors.shadow,
            )
            .clip(RoundedCornerShape(Vitt.radius.card))
            .background(colors.card)
            // The whole card opens the budget for its currency. Tapping the thing
            // the limit applies to needs no separate affordance, and the card has
            // no other action competing for the gesture.
            // `onClickLabel` names the action on the merged card. The card's own
            // figures already read out as its name, so it needs no
            // contentDescription — only a statement of what tapping does.
            .clickable(
                enabled = canSetBudget,
                onClickLabel = "Set a budget",
            ) { onSetBudget(ledger.currency) }
            .padding(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.hair),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Chroma lives in the dot, never in a filled card.
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(colors.currency(ledger.index)),
            )
            Text(
                "  ${ledger.currency.code}",
                style = Vitt.type.caption,
                color = colors.inkMuted,
            )
        }

        // Room remaining when a budget exists; otherwise what was spent.
        //
        // Never a negative hero figure. The one loud number on a screen must not
        // be bad news with a minus sign in front of it — that is the shape the
        // tone rules exist to prevent. Spending is stated as a positive amount
        // out, which is the same fact without the verdict.
        val over = remaining != null && remaining.minor < 0
        Text(
            // Over budget, the hero is what was *spent*, not what the overage
            // was. "€89.64 / past €1,800.00" was the overage as the loud
            // number, and €89.64 on a ledger card reads as a balance or as the
            // month's spending — it is neither. Revolut states spent-of-budget
            // and that is simply unambiguous. Under budget "left of" already
            // names its own figure, so the remaining amount stays the hero.
            text = (if (over) ledger.spent else remaining ?: ledger.spent).abs()
                .displayUnsigned(),
            style = Vitt.type.moneyHero,
            color = colors.ink,
            textAlign = TextAlign.Start,
        )
        Text(
            text = when {
                remaining != null && remaining.minor >= 0 ->
                    // A limit is a threshold, not a flow, so it takes no sign. `display()`
                    // prefixes "+" for anything positive, which rendered the budget
                    // as "left of +€1,800.00".
                    "left of ${ledger.budget!!.displayUnsigned()}"
                remaining != null ->
                    // Still no verdict and still no minus sign in front of the
                    // hero. "Over" closes the month; "past" with the days still
                    // to go leaves it open, which is what stops one bad week
                    // from writing off the rest of the month.
                    "${remaining.abs().displayUnsigned()} past ${ledger.budget!!.displayUnsigned()}" +
                        when (daysLeft) {
                            null -> ""
                            0 -> " · last day"
                            1 -> " · a day to go"
                            else -> " · $daysLeft days to go"
                        }
                // Says which period, because the card no longer only ever shows
                // a month. A budget line appears only at month grain — the
                // repository withholds it elsewhere rather than pro-rating it.
                canSetBudget -> "out $periodPhrase · tap to set a budget"
                else -> "out $periodPhrase"
            },
            style = Vitt.type.label,
            color = colors.inkMuted,
        )

        pressure?.let { HealthBar(it) }

        // Only what the hero figure does not already say. Repeating the same
        // number twice on one card wastes the "one loud number" rule it is
        // there to serve.
        // Over budget the hero is already the spent figure, so an "Out" beside
        // it would be the same number twice on one card.
        val showOut = remaining != null && !over
        val hasSecondary = ledger.received.minor != 0L || showOut
        if (hasSecondary) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Vitt.space.snug),
                horizontalArrangement = Arrangement.spacedBy(Vitt.space.section),
            ) {
                if (ledger.received.minor != 0L) Figure("In", ledger.received)
                if (showOut) Figure("Out", ledger.spent)
            }
        }
    }
}

/**
 * Budget health as accent fading to neutral.
 *
 * Never green to red: a red ledger is a verdict, and over budget is the absence
 * of accent rather than an alarm — the card goes quiet instead of shouting.
 */
@Composable
private fun HealthBar(pressure: Float) {
    val colors = Vitt.colors
    // Deliberately unlabelled. The line above it already says "left of EUR 1,800"
    // or "past EUR 1,800", so a reading of the bar would be the same fact twice.
    Box(
        Modifier.fillMaxWidth().height(3.dp).clip(CircleShape)
            .background(colors.inkFaint.copy(alpha = 0.25f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(pressure.coerceIn(0f, 1f))
                .height(3.dp)
                .clip(CircleShape)
                .background(colors.health(pressure)),
        )
    }
}

@Composable
private fun Figure(label: String, amount: Money) {
    // Inline rather than stacked. A single stacked stat under a hero figure
    // reads as an orphaned fragment; on one line it reads as a footnote, which
    // is what it is.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Vitt.type.caption, color = Vitt.colors.inkFaint)
        Text(
            // The label already says which way the money went, and `spent` is
            // held as a positive magnitude — so signing it produced the flatly
            // contradictory "Out +€1,852.04".
            "  " + amount.displayUnsigned(),
            style = Vitt.type.money.copy(fontSize = 14.sp),
            color = Vitt.colors.inkMuted,
        )
    }
}

@Composable
private fun NoTotalNote() {
    Text(
        "Kept separate on purpose. VITT never converts, so no figure here moves " +
            "because a rate did.",
        style = Vitt.type.label,
        color = Vitt.colors.inkMuted,
        modifier = Modifier.padding(top = Vitt.space.tight),
    )
}

@Composable
private fun OwedRow(owed: Map<Currency, Money>) {
    // Surfaced passively here, never as a push notification — and on a card, so
    // it reads as a standing fact rather than a stray link.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Vitt.radius.card))
            .background(Vitt.colors.accent.copy(alpha = 0.08f))
            .padding(Vitt.space.loose),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "You're owed",
            style = Vitt.type.body,
            color = Vitt.colors.inkMuted,
        )
        Text(
            "  " + owed.values.joinToString("  ·  ") { it.display() },
            style = Vitt.type.money,
            color = Vitt.colors.ink,
        )
    }
}

/**
 * The first home anybody sees, straight after setup.
 *
 * It used to be a line of grey text, and setup used to skip past it by opening
 * the Add sheet the moment Start was pressed, so the first thing after naming
 * your accounts was a keypad demanding a figure. Now setup lands here: the
 * companion says hello, and one button logs the first entry. The same one tap
 * it always was, but offered rather than imposed, with the accounts just
 * named visible underneath.
 */
@Composable
private fun EmptyLedgers(
    onImport: () -> Unit,
    onAdd: () -> Unit,
    companion: ie.shoonya.vitt.ui.CompanionAnimal?,
    currencies: Int,
) {
    val colors = Vitt.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(Vitt.radius.card))
            .background(colors.card)
            .padding(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Vitt.space.base),
        ) {
            if (companion != null) {
                Box(Modifier.semantics { contentDescription = "${companion.label}, your companion" }) {
                    ie.shoonya.vitt.ui.CompanionPet(
                        daysRecorded = 0,
                        currencyCount = currencies.coerceAtLeast(1),
                        animal = companion,
                        mood = null,
                        pixelSize = 3.dp,
                        pose = ie.shoonya.vitt.ui.CompanionPose.FRONT,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Vitt.space.hair)) {
                Text(
                    if (companion != null) "Hi, I'm ${companion.label}." else "You're set up.",
                    style = Vitt.type.title,
                    color = colors.ink,
                )
                Text(
                    "Log the last thing you paid for. It takes two taps.",
                    style = Vitt.type.body,
                    color = colors.inkMuted,
                )
            }
        }
        androidx.compose.material3.Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Text("Log something")
        }
        // Import lived only at the foot of the Reports tab, below the charts —
        // which is a fine place to find it again and a hopeless place to find
        // it the first time. Somebody arriving with a year of history has
        // nothing to report on yet, so the one screen they are looking at is
        // this one. Offered rather than pushed: a plain text button under the
        // sentence that tells them what the app expects instead.
        TextButton(onClick = onImport, contentPadding = PaddingValues(0.dp)) {
            Text("Or bring in a bank export", style = Vitt.type.label)
        }
    }
}

/** How far a card turns at the edge of a swipe. Enough to read as depth, not a spin. */
private const val TURN_DEGREES = 16f

/** The narrowest a card in the grid gets. */
private const val MIN_CARD_DP = 340

/** Room the side rail takes, and the widest the grid screens get (VittApp). */
private const val RAIL_DP = 100
private const val WIDE_CONTENT_DP = 1100

/** How many days of [period] remain after [today], or null when today is not in it. */
private fun daysLeft(period: ie.shoonya.vitt.time.Period?, today: Int): Int? =
    if (period != null && today in period) period.lastDay - today else null
