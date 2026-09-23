package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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

/**
 * A monthly limit for one currency.
 *
 * One currency at a time, because there is no such thing as a limit across two:
 * checking a combined figure would need a rate, and that rate moves. Setting them
 * separately is the honest shape, and it keeps this screen to one number.
 */
@Composable
fun BudgetSheet(
    currency: Currency,
    existing: Money?,
    onSet: (Money) -> Unit,
    onClear: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var entry by remember {
        mutableStateOf(
            existing?.let { AmountEntry.ofMagnitude(it) } ?: AmountEntry(currency = currency),
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Text(
                "${currency.code} a month",
                style = Vitt.type.title,
                color = Vitt.colors.ink,
            )
            Button(
                enabled = !entry.isEmpty && entry.money.minor > 0,
                onClick = { onSet(Money(entry.money.minor, currency)) },
            ) { Text("Set") }
        }

        Text(
            "What you mean to spend each month. Only ${currency.code} counts towards it.",
            style = Vitt.type.label,
            color = Vitt.colors.inkMuted,
        )

        AmountKeypad(entry = entry, onEntryChange = { entry = it })

        if (existing != null) {
            // Removing a budget has to be possible and has to be distinct from
            // setting it to zero, which would read as "spend nothing".
            TextButton(onClick = onClear) { Text("Remove this budget") }
        }
    }
}
