package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import ie.shoonya.vitt.model.AccountKind
import ie.shoonya.vitt.money.AmountEntry
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.ui.AmountKeypad
import ie.shoonya.vitt.ui.theme.Vitt

/**
 * Opening an account, in two steps.
 *
 * The steps are not decoration. A name needs the system keyboard and an opening
 * balance needs the keypad, and on iOS the IME covers the lower half of a bottom
 * sheet — so showing both at once makes the keypad's bottom row unreachable and
 * the amount impossible to finish. One input surface at a time is the only
 * layout that works, and it happens to read better anyway.
 */
@Composable
fun AccountSheet(
    onCreate: (name: String, currency: Currency, kind: AccountKind, opening: Money) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(Currency.EUR) }
    var kind by remember { mutableStateOf(AccountKind.CURRENT) }
    var entry by remember { mutableStateOf(AmountEntry(currency = currency)) }
    var settingOpening by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Content can exceed the sheet once the kind chips wrap, and a plain
            // Column clips rather than scrolls.
            .verticalScroll(rememberScrollState())
            .padding(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = if (settingOpening) ({ settingOpening = false }) else onCancel) {
                Text(if (settingOpening) "Back" else "Cancel")
            }
            Text(
                if (settingOpening) "Opening balance" else "New account",
                style = Vitt.type.title,
                color = Vitt.colors.ink,
            )
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    // A credit card's opening balance is what is already owed, so
                    // it goes in negative. Every other kind starts positive.
                    val signed = if (kind == AccountKind.CREDIT) {
                        Money(-entry.money.minor, currency)
                    } else {
                        entry.money
                    }
                    onCreate(name.trim(), currency, kind, signed)
                },
            ) { Text("Open") }
        }

        if (settingOpening) {
            Text(
                if (kind == AccountKind.CREDIT) {
                    "What you already owe on this card."
                } else {
                    "What is in the account today."
                },
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )
            AmountKeypad(entry = entry, onEntryChange = { entry = it })
        } else {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("AIB current") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Currency", style = Vitt.type.caption, color = Vitt.colors.inkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight)) {
                Currency.entries.forEach { c ->
                    FilterChip(
                        selected = currency == c,
                        onClick = {
                            currency = c
                            entry = entry.withCurrency(c)
                        },
                        label = { Text(c.code, style = Vitt.type.label) },
                    )
                }
            }
            Text(
                "Fixed once the account is open.",
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )

            Text("Kind", style = Vitt.type.caption, color = Vitt.colors.inkMuted)
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight),
                verticalArrangement = Arrangement.spacedBy(Vitt.space.tight),
            ) {
                AccountKind.entries.forEach { k ->
                    FilterChip(
                        selected = kind == k,
                        onClick = { kind = k },
                        label = { Text(k.label(), style = Vitt.type.label) },
                    )
                }
            }

            TextButton(onClick = { settingOpening = true }) {
                Text(
                    // The stored form, not the typed one: "€15.00", never "€15.".
                    if (!entry.hasValue) "Set an opening balance" else "Opening ${entry.money.displayUnsigned()}",
                )
            }
        }
    }
}

/** Sentence case for the UI; the persisted code stays lowercase and stable. */
fun AccountKind.label(): String = when (this) {
    AccountKind.CURRENT -> "Current"
    AccountKind.SAVINGS -> "Savings"
    AccountKind.CASH -> "Cash"
    AccountKind.CREDIT -> "Credit card"
    AccountKind.OTHER -> "Other"
}
