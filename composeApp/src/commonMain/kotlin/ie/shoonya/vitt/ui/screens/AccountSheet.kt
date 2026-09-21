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
import ie.shoonya.vitt.model.Account
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

            KindChips(kind = kind, onPick = { kind = it })

            TextButton(onClick = { settingOpening = true }) {
                Text(
                    // The stored form, not the typed one: "€15.00", never "€15.".
                    if (!entry.hasValue) "Set an opening balance" else "Opening ${entry.money.displayUnsigned()}",
                )
            }
        }
    }
}

/**
 * Correcting an account after the fact.
 *
 * A name typed once on a small keyboard is a name typed wrong sooner or later,
 * and until this existed the only remedy was opening a second account and
 * stranding the first one's history beside it.
 *
 * The name is applied on Save rather than as it is typed. Every field here is
 * one event in the log, and a rename per keystroke would put a dozen rows in
 * the user's own spreadsheet to turn "AIB" into "AIB current".
 *
 * The currency is shown but cannot be changed. It is fixed at creation because
 * the transactions already recorded are denominated in it, and re-denominating
 * them needs a rate the app refuses to invent (`PLAN.md` §0.6).
 */
@Composable
fun EditAccountSheet(
    account: Account,
    onSave: (name: String, kind: AccountKind) -> Unit,
    onArchivedChange: (Boolean) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the id, so opening the sheet on a second account starts from that
    // account rather than from whichever one was edited last.
    var name by remember(account.id) { mutableStateOf(account.name) }
    var kind by remember(account.id) { mutableStateOf(account.kind) }

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
            Text("Account", style = Vitt.type.title, color = Vitt.colors.ink)
            Button(
                enabled = name.isNotBlank(),
                onClick = { onSave(name.trim(), kind) },
            ) { Text("Save") }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Currency", style = Vitt.type.caption, color = Vitt.colors.inkMuted)
        Text(account.currency.code, style = Vitt.type.body, color = Vitt.colors.ink)
        Text(
            "Fixed once the account is open.",
            style = Vitt.type.label,
            color = Vitt.colors.inkMuted,
        )

        KindChips(kind = kind, onPick = { kind = it })

        TextButton(
            onClick = { onArchivedChange(!account.archived) },
            modifier = Modifier.padding(top = Vitt.space.snug),
        ) {
            Text(
                if (account.archived) "Unarchive" else "Archive",
                color = if (account.archived) Vitt.colors.ink else Vitt.colors.destructive,
            )
        }
        Text(
            if (account.archived) {
                "Brings it back to the pickers."
            } else {
                // Said plainly, because a red "Archive" reads as "delete" and
                // the whole point of archiving is that nothing is lost.
                "Takes it out of the pickers. Its entries stay in the ledger."
            },
            style = Vitt.type.label,
            color = Vitt.colors.inkMuted,
        )
    }
}

/** The kind row, shared by opening an account and correcting one. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun KindChips(kind: AccountKind, onPick: (AccountKind) -> Unit) {
    Text("Kind", style = Vitt.type.caption, color = Vitt.colors.inkMuted)
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.tight),
    ) {
        AccountKind.entries.forEach { k ->
            FilterChip(
                selected = kind == k,
                onClick = { onPick(k) },
                label = { Text(k.label(), style = Vitt.type.label) },
            )
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
