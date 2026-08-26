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
import ie.shoonya.vitt.model.LedgerRepository
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.ui.screens.ActivityScreen
import ie.shoonya.vitt.ui.screens.AddScreen
import ie.shoonya.vitt.ui.screens.HabitScreen
import ie.shoonya.vitt.ui.screens.LedgersScreen
import ie.shoonya.vitt.ui.theme.Vitt

private enum class Tab(val label: String) {
    Ledgers("Ledgers"),
    Activity("Activity"),
    People("Ledger"),
    Habit("Habit"),
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
    var showAdd by remember { mutableStateOf(false) }
    // Bumped after a write so the screens re-read the log.
    var revision by remember { mutableStateOf(0) }

    val ledgers = remember(revision) { repository.ledgers() }
    val days = remember(revision) { repository.byDay() }
    val owed = remember(revision) { repository.owed() }
    val recorded = remember(revision) { repository.daysRecorded(today) }
    val indexOf: (Currency) -> Int = { c -> ledgers.firstOrNull { it.currency == c }?.index ?: 0 }

    Column(modifier = modifier.fillMaxSize().background(Vitt.colors.ground)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (tab) {
                Tab.Ledgers -> LedgersScreen(ledgers, owed, daysRecorded = recorded)
                Tab.Activity -> ActivityScreen(days, indexOf)
                Tab.People -> PeopleScreen(owed)
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
            onAdd = { showAdd = true },
        )
    }

    if (showAdd) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAdd = false },
            sheetState = sheetState,
            containerColor = Vitt.colors.ground,
        ) {
            AddScreen(
                currencies = ledgers.map { it.currency }.ifEmpty { listOf(Currency.EUR, Currency.INR) },
                onSave = { amount, merchant, category ->
                    repository.record(
                        id = newId(),
                        amount = amount,
                        day = today,
                        merchant = merchant,
                        category = category,
                    )
                    revision++
                    showAdd = false
                },
                onCancel = { showAdd = false },
            )
        }
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
    Box(
        modifier = modifier.clickable { onSelect(tab) }.padding(vertical = Vitt.space.tight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            tab.label,
            style = Vitt.type.caption,
            textAlign = TextAlign.Center,
            color = if (selected) Vitt.colors.accent else Vitt.colors.inkMuted,
        )
    }
}

/**
 * People and what is owed.
 *
 * The unit is a person, not an entry, and amounts stay in the currency they were
 * incurred in — someone can owe you in two currencies at once and the app will
 * not net them off.
 */
@Composable
private fun PeopleScreen(owed: Map<Currency, ie.shoonya.vitt.money.Money>) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
    ) {
        Text("Ledger", style = Vitt.type.title, color = Vitt.colors.ink)
        if (owed.isEmpty()) {
            Text(
                "Nobody owes you anything. Split an expense and it will show up here.",
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )
        } else {
            owed.forEach { (currency, amount) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(currency.code, style = Vitt.type.body, color = Vitt.colors.inkMuted)
                    Text(amount.display(), style = Vitt.type.money, color = Vitt.colors.ink)
                }
            }
            Text(
                "Kept in the currency it was incurred in. Two currencies are never netted off.",
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )
        }
    }
}
