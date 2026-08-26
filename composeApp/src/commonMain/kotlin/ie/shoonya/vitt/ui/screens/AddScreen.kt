package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ie.shoonya.vitt.money.AmountEntry
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.ui.AmountKeypad
import ie.shoonya.vitt.ui.theme.Vitt

/** What the user is recording. Direction is chosen, never inferred. */
enum class EntryKind { Expense, Income }

/**
 * Two taps to log: type the amount, then Save.
 *
 * Everything else is optional and pre-guessed. Capture is the only thing that
 * happens daily, so it owns the shortest path — every extra required field is a
 * future uninstall.
 */
@Composable
fun AddScreen(
    currencies: List<Currency>,
    onSave: (amount: Money, merchant: String?, category: String?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var entry by remember { mutableStateOf(AmountEntry(currency = currencies.firstOrNull() ?: Currency.EUR)) }
    var kind by remember { mutableStateOf(EntryKind.Expense) }
    var category by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxWidth().padding(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Text("New", style = Vitt.type.title, color = Vitt.colors.ink)
            Button(
                enabled = !entry.isEmpty,
                onClick = {
                    // The keypad holds a magnitude; the sign comes from the
                    // chosen direction, never guessed from the input.
                    val signed = when (kind) {
                        EntryKind.Expense -> Money(-entry.money.minor, entry.currency)
                        EntryKind.Income -> entry.money
                    }
                    onSave(signed, null, category)
                },
            ) { Text("Save") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Vitt.space.snug)) {
            EntryKind.entries.forEach { k ->
                FilterChip(
                    selected = kind == k,
                    onClick = { kind = k },
                    label = { Text(k.name) },
                )
            }
        }

        if (currencies.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(Vitt.space.snug)) {
                currencies.forEach { c ->
                    FilterChip(
                        selected = entry.currency == c,
                        onClick = { entry = entry.withCurrency(c) },
                        label = { Text(c.code) },
                    )
                }
            }
        }

        AmountKeypad(entry = entry, onEntryChange = { entry = it })

        // Deliberately no text fields on this screen.
        //
        // The keypad exists to avoid the system IME, which is Compose
        // Multiplatform's weakest surface on iOS. Putting a text field beside it
        // summons that keyboard anyway — and in a bottom sheet it covers the
        // lower half of the keypad, so 7, 8, 9, 0, C and delete become
        // unreachable and the amount cannot be finished.
        //
        // Category is chosen from a list rather than typed, which is what the
        // design specifies: it arrives pre-filled from the learned rules, so
        // free text was never the intended input.
        Text("Category", style = Vitt.type.caption, color = Vitt.colors.inkMuted)
        CategoryChips(selected = category, onSelect = { category = it })

        Text(
            "Two taps: type, then Save. The category is optional.",
            style = Vitt.type.label,
            color = Vitt.colors.inkMuted,
        )
    }
}

/**
 * The default category set, as chips.
 *
 * Twelve fixed categories, assigned by purpose rather than by merchant — a work
 * coffee and a personal coffee are both Food. Chips rather than a text field
 * because the design has the category arriving pre-filled from the learned
 * rules; typing it was never the intended path, and a text field here would
 * summon the keyboard over the keypad.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(selected: String?, onSelect: (String?) -> Unit) {
    val categories = listOf(
        "Groceries", "Dining", "Transport", "Housing", "Utilities", "Health",
        "Shopping", "Entertainment", "Travel", "Family", "Subscriptions", "Other",
    )
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.tight),
    ) {
        categories.forEach { name ->
            FilterChip(
                selected = selected == name,
                // Tapping the chosen one clears it: the field is optional, so
                // there has to be a way back to none.
                onClick = { onSelect(if (selected == name) null else name) },
                label = { Text(name, style = Vitt.type.label) },
            )
        }
    }
}
