package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import ie.shoonya.vitt.model.SplitDraft
import ie.shoonya.vitt.model.Transaction
import ie.shoonya.vitt.money.AmountEntry
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.ui.AmountKeypad
import ie.shoonya.vitt.ui.theme.Vitt

/** What the keypad is typing when it takes the whole sheet. */
private sealed interface Typing {
    data object Bill : Typing
    data class PaidBack(val who: String) : Typing
    /** A repayment on a split with nobody named, which has only the one total. */
    data object Repayment : Typing
}

/**
 * A saved split, opened again: the same editor the Add sheet uses, then who has
 * paid back.
 *
 * The figures are a draft until Save, because they only mean anything together
 * and the log they go to keeps every version. Repayments are not part of the
 * draft: "Bea paid me back" is a fact the moment it happens, and it is recorded
 * then, against the split as saved.
 */
@Composable
fun SplitSheet(
    split: Transaction,
    /** People split with before, most recent first, for one-tap rows. */
    knownPeople: List<String>,
    onSettle: (Money) -> Unit,
    onSettleInFull: () -> Unit,
    onSettlePerson: (who: String, received: Money) -> Unit,
    /** Saves the figures and the people. Returns why it could not, or null. */
    onEditSplit: (total: Money, yours: Money, others: Map<String, Money>?, people: Set<String>) -> String?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currency = split.amount.currency
    val saved = remember(split) { SplitDraft.of(split) }
    var draft by remember(split) { mutableStateOf(saved) }
    var typing by remember(split) { mutableStateOf<Typing?>(null) }
    var entry by remember(split) { mutableStateOf(AmountEntry(currency = currency)) }
    var current by remember(split) { mutableStateOf<Money?>(null) }
    var naming by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var problem by remember(split) { mutableStateOf<String?>(null) }
    val dirty = draft != saved

    fun type(what: Typing, now: Money?) {
        typing = what
        current = now
        entry = AmountEntry(currency = currency)
    }

    // The header is pinned and only the body scrolls, as on the Add sheet and
    // for the same reason: with Save inside the scroll, typing a part at the
    // foot of the list left Save out of sight above it. The cap is what gives
    // the scroll a viewport, since a bottom sheet hands its content an
    // unbounded height.
    var partFocus by remember(split) { mutableStateOf<String?>(null) }
    val bodyMaxHeight = ie.shoonya.vitt.ui.sheetBodyMaxHeight(
        ie.shoonya.vitt.ui.SHEET_CHROME + if (partFocus != null && typing == null) ie.shoonya.vitt.ui.PART_KEYPAD else 0.dp,
    )
    val bodyScroll = rememberScrollState()
    Column(
        modifier = modifier.fillMaxWidth().padding(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = when {
                    typing != null -> ({ typing = null })
                    dirty -> ({ draft = saved })
                    else -> onDone
                },
            ) { Text(if (typing != null) "Back" else if (dirty) "Undo" else "Done") }
            Text(
                split.merchantLabel ?: split.categoryOrNull?.label ?: "Split",
                style = Vitt.type.title,
                color = Vitt.colors.ink,
            )
            when (val what = typing) {
                null -> if (dirty) {
                    Button(
                        enabled = draft.isValid,
                        onClick = {
                            problem = onEditSplit(draft.total, draft.yours, draft.othersToSave, draft.people.toSet())
                        },
                    ) { Text("Save") }
                } else {
                    TextButton(onClick = onSettleInFull, enabled = !split.isSettled) { Text("Settled up") }
                }
                else -> Button(
                    enabled = entry.hasValue || what is Typing.PaidBack,
                    onClick = {
                        val value = Money(entry.money.minor, currency)
                        when (what) {
                            Typing.Bill -> draft = draft.withTotal(value)
                            is Typing.PaidBack -> onSettlePerson(what.who, value)
                            Typing.Repayment -> onSettle(value)
                        }
                        typing = null
                    },
                ) { Text("Set") }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = bodyMaxHeight)
                .verticalScroll(bodyScroll),
            verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
        ) {
            typing?.let { what ->
                val now = current?.let { " Now ${it.displayUnsigned()}." }.orEmpty()
                Text(
                    when (what) {
                        Typing.Bill -> "The whole bill.$now"
                        is Typing.PaidBack -> "What ${what.who} has paid back so far.$now"
                        Typing.Repayment -> "The total that has come back, not just this instalment.$now"
                    },
                    style = Vitt.type.label,
                    color = Vitt.colors.inkMuted,
                )
                AmountKeypad(entry = entry, onEntryChange = { entry = it })
                return@Column
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Bill", style = Vitt.type.label, color = Vitt.colors.inkMuted)
                Text(
                    draft.total.displayUnsigned(),
                    style = Vitt.type.money,
                    color = Vitt.colors.accent,
                    modifier = Modifier
                        .clickable { type(Typing.Bill, draft.total) }
                        .semantics {
                            role = Role.Button
                            contentDescription = "Bill ${draft.total.displayUnsigned()}. Change"
                        },
                )
            }

            SplitEditor(
                draft = draft,
                onDraft = { draft = it },
                suggestions = knownPeople,
                onNewPerson = { naming = true },
                focus = partFocus,
                onFocus = { partFocus = it },
            )
            if (naming) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Vitt.space.snug),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text("Email or name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = typed.isNotBlank(),
                        onClick = {
                            draft = draft.toggle(typed)
                            typed = ""
                            naming = false
                        },
                    ) { Text("Add") }
                }
            }
            problem?.let { Text(it, style = Vitt.type.label, color = Vitt.colors.destructive) }

            // Repayments are against the split as saved, so they wait for Save.
            if (!dirty) {
                if (split.splitWith.isEmpty()) {
                    if (!split.isSettled && split.isSplit) {
                        TextButton(onClick = { type(Typing.Repayment, split.settled) }) { Text("Record a repayment") }
                    }
                } else {
                    Text("Paid back", style = Vitt.type.caption, color = Vitt.colors.inkMuted)
                    split.splitWith.sorted().forEach { who ->
                        RepaymentLine(
                            who = who,
                            outstanding = split.outstandingFor(who),
                            onPaidBack = { onSettlePerson(who, split.shares().getValue(who)) },
                            onPart = { type(Typing.PaidBack(who), split.settledBy[who]) },
                        )
                    }
                }
            }
        }

        // Pinned under the body, not at its end: see SplitPartKeypad.
        if (typing == null) {
            partFocus?.let { who ->
                SplitPartKeypad(draft = draft, who = who, onDraft = { draft = it }, onDone = { partFocus = null })
            }
        }
    }
}

@Composable
private fun RepaymentLine(who: String, outstanding: Money?, onPaidBack: () -> Unit, onPart: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(who, style = Vitt.type.body, color = Vitt.colors.ink, modifier = Modifier.weight(1f))
        if (outstanding == null || outstanding.minor == 0L) {
            Text("Paid back", style = Vitt.type.label, color = Vitt.colors.inkMuted)
        } else {
            Text("Owes ${outstanding.displayUnsigned()}", style = Vitt.type.label, color = Vitt.colors.inkMuted)
            TextButton(
                onClick = onPaidBack,
                modifier = Modifier.semantics { contentDescription = "$who paid back in full" },
            ) { Text("Paid") }
            TextButton(
                onClick = onPart,
                modifier = Modifier.semantics { contentDescription = "$who paid back part" },
            ) { Text("Part") }
        }
    }
}
