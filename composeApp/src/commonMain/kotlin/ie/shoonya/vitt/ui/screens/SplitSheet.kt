package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.InputChip
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
import ie.shoonya.vitt.model.Transaction
import ie.shoonya.vitt.money.AmountEntry
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.ui.AmountKeypad
import ie.shoonya.vitt.ui.theme.Vitt

/**
 * One split: who was in on it, and what has come back.
 *
 * Participants are typed here rather than on the add screen, because naming
 * someone needs the system keyboard and the add screen's keypad cannot share a
 * bottom sheet with it. Splitting is also rarer than logging, so it is the right
 * thing to move off the daily path.
 */
@Composable
fun SplitSheet(
    split: Transaction,
    onAddParticipant: (String) -> Unit,
    onRemoveParticipant: (String) -> Unit,
    onSettle: (Money) -> Unit,
    onSettleInFull: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var typed by remember { mutableStateOf("") }
    var settling by remember { mutableStateOf(false) }
    var entry by remember { mutableStateOf(AmountEntry(currency = split.amount.currency)) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // A split with several participants plus the figures block can exceed
            // the sheet, and a plain Column clips rather than scrolls.
            .verticalScroll(rememberScrollState())
            .padding(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = if (settling) ({ settling = false }) else onDone) {
                Text(if (settling) "Back" else "Done")
            }
            Text(
                split.merchantLabel ?: split.categoryOrNull?.label ?: "Split",
                style = Vitt.type.title,
                color = Vitt.colors.ink,
            )
            if (settling) {
                Button(
                    enabled = !entry.isEmpty,
                    onClick = {
                        onSettle(Money(entry.money.minor, split.amount.currency))
                        settling = false
                    },
                ) { Text("Record") }
            } else {
                TextButton(
                    onClick = onSettleInFull,
                    enabled = !split.isSettled,
                ) { Text("Settled up") }
            }
        }

        if (settling) {
            Text(
                "The total that has come back, not just this instalment.",
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )
            AmountKeypad(entry = entry, onEntryChange = { entry = it })
            return@Column
        }

        split.totalPaid?.let { paid ->
            Column(verticalArrangement = Arrangement.spacedBy(Vitt.space.hair)) {
                Figure("Paid", paid.abs())
                Figure("Your share", split.amount.abs())
                split.owed()?.let { Figure("Owed to you", it) }
                if (split.settled.minor > 0) Figure("Come back", split.settled)
                split.outstanding()?.let { Figure("Still out", it) }
            }
        }

        Text("Split with", style = Vitt.type.caption, color = Vitt.colors.inkMuted)
        if (split.splitWith.isEmpty()) {
            Text(
                "Nobody named yet. These are labels for your own records — nothing " +
                    "is sent, and their app never learns about it.",
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )
        } else {
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight),
                verticalArrangement = Arrangement.spacedBy(Vitt.space.tight),
            ) {
                split.splitWith.sorted().forEach { who ->
                    InputChip(
                        selected = false,
                        onClick = { onRemoveParticipant(who) },
                        label = { Text(who, style = Vitt.type.label) },
                        trailingIcon = { Text("×", style = Vitt.type.label) },
                    )
                }
            }
        }

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
                    onAddParticipant(typed)
                    typed = ""
                },
            ) { Text("Add") }
        }
    }
}

@Composable
private fun Figure(label: String, amount: Money) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = Vitt.type.label, color = Vitt.colors.inkMuted)
        Text(amount.displayUnsigned(), style = Vitt.type.money, color = Vitt.colors.ink)
    }
}
