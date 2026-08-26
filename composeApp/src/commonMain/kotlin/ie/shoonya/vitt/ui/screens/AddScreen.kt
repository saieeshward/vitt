package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
    var merchant by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }

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
                    onSave(
                        signed,
                        merchant.takeIf { it.isNotBlank() },
                        category.takeIf { it.isNotBlank() },
                    )
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

        OutlinedTextField(
            value = merchant,
            onValueChange = { merchant = it },
            label = { Text("Where") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text("Category") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            "Two taps: type, then Save. Nothing below the amount is required.",
            style = Vitt.type.label,
            color = Vitt.colors.inkMuted,
        )
    }
}
