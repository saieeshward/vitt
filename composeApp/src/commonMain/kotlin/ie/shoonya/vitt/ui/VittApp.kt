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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ie.shoonya.vitt.model.LedgerRepository
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.model.SettlementSummary
import ie.shoonya.vitt.ui.screens.AccountSheet
import ie.shoonya.vitt.ui.screens.ActivityScreen
import ie.shoonya.vitt.ui.screens.AddScreen
import ie.shoonya.vitt.ui.screens.HabitScreen
import ie.shoonya.vitt.ui.screens.LedgersScreen
import ie.shoonya.vitt.ui.screens.PeopleScreen
import ie.shoonya.vitt.ui.screens.SplitSheet
import ie.shoonya.vitt.ui.screens.TransferSheet
import ie.shoonya.vitt.ui.theme.Vitt

private enum class Tab(val label: String, val icon: VittIcon) {
    Ledgers("Ledgers", VittIcon.Wallet),
    Activity("Activity", VittIcon.List),
    People("Ledger", VittIcon.People),
    Habit("Habit", VittIcon.Spark),
}

/** Which bottom sheet is open. Only one can be, so this is a state, not four flags. */
private sealed interface Sheet {
    data object Add : Sheet
    data object NewAccount : Sheet
    data object Transfer : Sheet
    data class Split(val id: String) : Sheet
    data class Summary(val subject: String, val body: String) : Sheet
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
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(Tab.Ledgers) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    // Bumped after a write so the screens re-read the log.
    var revision by remember { mutableStateOf(0) }

    val ledgers = remember(revision) { repository.ledgers() }
    val days = remember(revision) { repository.byDay() }
    val owed = remember(revision) { repository.owed() }
    val recorded = remember(revision) { repository.daysRecorded(today) }
    val accounts = remember(revision) { repository.accounts() }
    val balances = remember(revision) { repository.accountBalances() }
    val transfers = remember(revision) { repository.transfers() }
    val participants = remember(revision) { repository.openSplitParticipants() }
    val indexOf: (Currency) -> Int = { c -> ledgers.firstOrNull { it.currency == c }?.index ?: 0 }
    val nameOf: (String) -> String = { id ->
        accounts.firstOrNull { it.id == id }?.name ?: "unknown account"
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

    Column(modifier = modifier.fillMaxSize().background(Vitt.colors.ground)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (tab) {
                Tab.Ledgers -> LedgersScreen(
                    ledgers = ledgers,
                    owed = owed,
                    daysRecorded = recorded,
                    balances = balances,
                    transfers = transfers,
                    onAddAccount = { sheet = Sheet.NewAccount },
                    onTransfer = { sheet = Sheet.Transfer },
                    accountName = nameOf,
                    formatDay = formatDay,
                )
                Tab.Activity -> ActivityScreen(days, indexOf)
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
                    formatDay = formatDay,
                )
                Tab.Habit -> HabitScreen(
                    daysRecorded = recorded,
                    windowDays = 30,
                    currencyCount = ledgers.size,
                )
            }
        }

        TabBar(
            current = tab,
            onSelect = { tab = it },
            onAdd = { sheet = Sheet.Add },
        )
    }

    sheet?.let { open ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = sheetState,
            containerColor = Vitt.colors.ground,
        ) {
            when (open) {
                Sheet.Add -> AddScreen(
                    currencies = accounts.map { it.currency }.distinct()
                        .ifEmpty { ledgers.map { it.currency } }
                        .ifEmpty { listOf(Currency.EUR, Currency.INR) },
                    accounts = accounts,
                    onSave = { amount, merchant, category, accountId, totalPaid ->
                        repository.record(
                            id = newId(),
                            amount = amount,
                            day = today,
                            merchant = merchant,
                            category = category,
                            accountId = accountId,
                            totalPaid = totalPaid,
                        )
                        revision++
                        sheet = null
                    },
                    onCancel = { sheet = null },
                )

                Sheet.NewAccount -> AccountSheet(
                    onCreate = { name, currency, kind, opening ->
                        repository.openAccount(newId(), name, currency, kind, opening)
                        revision++
                        sheet = null
                    },
                    onCancel = { sheet = null },
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
                        revision++
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
                                repository.addSplitParticipant(open.id, it); revision++
                            },
                            onRemoveParticipant = {
                                repository.removeSplitParticipant(open.id, it); revision++
                            },
                            onSettle = { repository.settle(open.id, it); revision++ },
                            onSettleInFull = {
                                repository.settleInFull(open.id); revision++; sheet = null
                            },
                            onDone = { sheet = null },
                        )
                    }
                }

                is Sheet.Summary -> SummarySheet(
                    subject = open.subject,
                    body = open.body,
                    onDone = { sheet = null },
                )
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
            "Amounts are written plainly on purpose: the reader's locale is " +
                "unknowable, and 1.234 means two different numbers depending on where " +
                "they are.",
            style = Vitt.type.label,
            color = Vitt.colors.inkFaint,
        )
    }
}

@Composable
private fun TabBar(current: Tab, onSelect: (Tab) -> Unit, onAdd: () -> Unit) {
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
            TabItem(it, current, onSelect, Modifier.weight(1f))
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
                    .clip(CircleShape)
                    .background(colors.accent)
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", style = Vitt.type.title, color = colors.ground)
            }
        }

        listOf(Tab.People, Tab.Habit).forEach {
            TabItem(it, current, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TabItem(tab: Tab, current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier) {
    val selected = tab == current
    val tint = if (selected) Vitt.colors.accent else Vitt.colors.inkMuted
    Column(
        modifier = modifier.clickable { onSelect(tab) }.padding(vertical = Vitt.space.hair),
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

