package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ie.shoonya.vitt.model.SplitDraft
import ie.shoonya.vitt.model.SplitDraft.Companion.YOU
import ie.shoonya.vitt.money.AmountEntry
import ie.shoonya.vitt.ui.AmountKeypad
import ie.shoonya.vitt.ui.theme.Vitt

/**
 * Who a bill is for, and each person's part of it, on one screen.
 *
 * Tricount's shape, which is also Splitwise's: everybody is a row with a tick
 * and an amount, the amounts are always on screen, and ticking somebody in or
 * out re-splits at once. A row's amount is typed on the drawn keypad under the
 * list, with the list still there, and the rows nobody typed into share what is
 * left ([SplitDraft]). There is no separate mode and no Set button: what is on
 * screen is the split.
 *
 * People already split with are offered as rows to tick, so the weekly ones
 * need no keyboard. A new name does, which is why [onNewPerson] hands that to
 * the caller: on the Add sheet the system keyboard cannot share the screen with
 * the keypad.
 */
@Composable
fun SplitEditor(
    draft: SplitDraft,
    onDraft: (SplitDraft) -> Unit,
    /** People split with before, most recent first. Those already on it are skipped. */
    suggestions: List<String>,
    onNewPerson: () -> Unit,
    /** Whose part the pinned keypad is typing, or null. The caller places [SplitPartKeypad]. */
    focus: String?,
    onFocus: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val amounts = draft.amounts
    val offered = suggestions.map { it.trim().lowercase() }.filter { it.isNotEmpty() && it !in draft.people }.distinct()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Vitt.space.tight)) {
        // The one figure a split is about, drawn as well as written: what the
        // user is out of pocket, against the whole bill. It moves as parts
        // are typed, so the effect of each keystroke is visible at a glance.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vitt.space.base)) {
            ie.shoonya.vitt.ui.SplitRing(yours = draft.yours, others = draft.others.values.toList())
            Column {
                Text("Your part ${draft.yours.displayUnsigned()}", style = Vitt.type.body, color = Vitt.colors.ink)
                Text(
                    // "Between 1" is not a split; with only the user on it the
                    // line says what the bill is and invites the rest.
                    if (draft.people.isEmpty()) "of ${draft.total.displayUnsigned()}. Tick who else was in on it."
                    else "of ${draft.total.displayUnsigned()}, between ${draft.everyone.size}",
                    style = Vitt.type.label,
                    color = Vitt.colors.inkMuted,
                )
            }
        }
        Text("For whom", style = Vitt.type.caption, color = Vitt.colors.inkMuted)

        draft.everyone.forEach { who ->
            PersonLine(
                name = if (who == YOU) "You" else who,
                included = true,
                // You are always on your own bill: you paid it.
                onToggle = if (who == YOU) null else ({ if (focus == who) onFocus(null); onDraft(draft.toggle(who)) }),
                amount = amounts.getValue(who).displayUnsigned(),
                typed = who in draft.fixed,
                focused = focus == who,
                onAmount = { onFocus(if (focus == who) null else who) },
            )
        }
        offered.forEach { who ->
            PersonLine(
                name = who,
                included = false,
                onToggle = { onDraft(draft.toggle(who)) },
                amount = null,
                typed = false,
                focused = false,
                onAmount = {},
            )
        }
        TextButton(onClick = onNewPerson) { Text("+ Someone new") }

        val over = draft.over
        val unassigned = draft.unassigned
        when {
            over != null -> Warning("${over.displayUnsigned()} more than the bill")
            unassigned != null -> Warning("${unassigned.displayUnsigned()} not given to anyone")
        }
        if (!draft.isEqual) {
            TextButton(onClick = { onFocus(null); onDraft(draft.equal()) }) { Text("Split equally") }
        }

    }
}

/**
 * The keypad for one person's part, pinned by the caller below the scrolling
 * list rather than at the end of it, the way a system keyboard sits under what
 * it types into. At the end of the list it was a scroll away, and scrolling it
 * "into view" inside a bottom sheet left its bottom row, and the 0 key, under
 * the sheet's edge. Pinned, it is always whole and the rows above it stay in
 * sight as their parts move.
 */
@Composable
fun SplitPartKeypad(
    draft: SplitDraft,
    who: String,
    onDraft: (SplitDraft) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Opens empty: a keypad seeded with "20.00" has no room for a digit.
    var entry by remember(who) { mutableStateOf(AmountEntry(currency = draft.total.currency)) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Vitt.space.hair)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (who == YOU) "Your part" else "$who's part",
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )
            TextButton(onClick = onDone) { Text("Done") }
        }
        AmountKeypad(
            entry = entry,
            onEntryChange = {
                entry = it
                // Live: the rows above move as it is typed. An emptied field
                // hands the row back to "share the rest" rather than pinning it
                // at zero.
                onDraft(if (it.hasValue) draft.set(who, it.money) else draft.unset(who))
            },
        )
    }
}

@Composable
private fun PersonLine(
    name: String,
    included: Boolean,
    onToggle: (() -> Unit)?,
    amount: String?,
    typed: Boolean,
    focused: Boolean,
    onAmount: () -> Unit,
) {
    val colors = Vitt.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) colors.surface else colors.ground)
            .padding(horizontal = Vitt.space.tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Vitt.space.snug),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .let {
                    if (onToggle == null) {
                        it.semantics { contentDescription = "$name, on the bill" }
                    } else {
                        it.toggleable(value = included, role = Role.Checkbox, onValueChange = { onToggle() })
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Vitt.space.snug),
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (included) colors.accent else colors.hairline)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                if (included) Text("✓", style = Vitt.type.label, color = colors.ground)
            }
            Text(name, style = Vitt.type.body, color = if (included) colors.ink else colors.inkMuted)
        }
        if (amount != null) {
            Text(
                amount,
                style = Vitt.type.money,
                // A typed amount reads as typed; a shared one as worked out.
                color = if (typed || focused) colors.accent else colors.inkMuted,
                modifier = Modifier
                    .clickable(onClick = onAmount)
                    .semantics {
                        role = Role.Button
                        contentDescription = (if (name == "You") "Your part" else "$name's part") + " $amount. Change"
                    }
                    .padding(Vitt.space.tight),
            )
        }
    }
}

@Composable
private fun Warning(text: String) {
    Text(text, style = Vitt.type.label, color = Vitt.colors.destructive)
}