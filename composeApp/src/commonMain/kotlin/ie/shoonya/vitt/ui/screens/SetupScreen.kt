package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import ie.shoonya.vitt.money.AccountSuggestions
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.deviceCurrency
import ie.shoonya.vitt.ui.theme.Vitt

/** One account named during setup, before any of it is written to the log. */
data class DraftAccount(val name: String, val currency: Currency)

/**
 * The first screen on a cold install: name the accounts, then start.
 *
 * Not a walkthrough, and deliberately so. A carousel of value propositions would
 * fight the product's own pitch — no account, open it and start — and it cannot
 * do the work anyway, because the work is a decision rather than a fact. Nobody
 * forgets that a spending app tracks spending. What they do not know is that
 * this one wants their accounts named first.
 *
 * What made the screen necessary: the first entry on an empty app lands with a
 * null account, because the person has none and nothing told them accounts
 * existed. The Accounts section sits below the currency cards on the home tab,
 * so the concept the whole product is built on was found by scrolling past your
 * own orphaned entry, or not at all. That is a sequencing problem, which is why
 * the fix is a step rather than an explanation.
 *
 * Two fields the account sheet asks for are deliberately not asked here:
 *
 * - **Opening balance.** The highest-friction field there is. It needs the
 *   keypad, and it often needs the person to go and look it up in another app.
 *   It blocks nothing: an account with no opening balance is a correct account
 *   whose balance is relative rather than absolute.
 * - **Kind.** Everything defaults to Current. Kind decides whether a negative
 *   balance reads as "owed" or "overdrawn" and nothing else, so asking for it up
 *   front gives it a weight it does not carry.
 *
 * Both are one tap away the moment setup ends, because an account row opens
 * [EditAccountSheet]. This screen is only ever allowed to be the fast path.
 */
@Composable
fun SetupScreen(
    onDone: (List<DraftAccount>) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The device region is a starting guess and never more than that: the
    // premise of the app is people whose money is in more than one currency, so
    // the second currency is the interesting one and it cannot be inferred.
    var currency by remember { mutableStateOf(deviceCurrency() ?: Currency.EUR) }
    var typed by remember { mutableStateOf("") }
    val drafts = remember { mutableStateListOf<DraftAccount>() }

    val add: (String) -> Unit = { name ->
        val clean = name.trim()
        // Same name twice in the same currency is a mis-tap, not an intention.
        // Across currencies it is ordinary: "Revolut" in EUR and in GBP are two
        // accounts, which is exactly what the one-currency-per-account rule means.
        if (clean.isNotBlank() && drafts.none { it.name == clean && it.currency == currency }) {
            drafts.add(DraftAccount(clean, currency))
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Where does your money sit?", style = Vitt.type.title, color = Vitt.colors.ink)
            // Plain, and never hidden. Nothing in the app depends on this screen
            // having been answered, so pretending otherwise would be a lie told
            // to raise a completion rate.
            TextButton(onClick = onSkip) { Text("Skip") }
        }
        Text(
            "Name them however you think of them. You can change any of this later.",
            style = Vitt.type.label,
            color = Vitt.colors.inkMuted,
        )

        Text("Currency", style = Vitt.type.caption, color = Vitt.colors.inkMuted)
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight),
            verticalArrangement = Arrangement.spacedBy(Vitt.space.tight),
        ) {
            Currency.entries.forEach { c ->
                FilterChip(
                    selected = currency == c,
                    onClick = { currency = c },
                    label = { Text(c.code, style = Vitt.type.label) },
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Vitt.space.snug),
        ) {
            if (drafts.isNotEmpty()) {
                item {
                    Text("Added", style = Vitt.type.caption, color = Vitt.colors.inkMuted)
                }
                items(drafts.toList()) { draft ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {},
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${draft.name} · ${draft.currency.code}",
                            style = Vitt.type.body,
                            color = Vitt.colors.ink,
                        )
                        TextButton(onClick = { drafts.remove(draft) }) {
                            Text("Remove", color = Vitt.colors.destructive)
                        }
                    }
                }
            }

            item {
                Text(
                    if (drafts.isEmpty()) "Tap the ones you have" else "Add another",
                    style = Vitt.type.caption,
                    color = Vitt.colors.inkMuted,
                    modifier = Modifier.padding(top = Vitt.space.snug),
                )
            }
            item {
                // The chips carry the whole screen. Five accounts in twenty
                // seconds of tapping is the difference between a person who
                // finishes setup and one who quits partway and never comes back
                // to it, and typing five bank names on a phone is the slow path
                // that makes them quit.
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight),
                    verticalArrangement = Arrangement.spacedBy(Vitt.space.tight),
                ) {
                    AccountSuggestions.forCurrency(currency).forEach { name ->
                        val taken = drafts.any { it.name == name && it.currency == currency }
                        FilterChip(
                            selected = taken,
                            enabled = !taken,
                            onClick = { add(name) },
                            label = { Text(name, style = Vitt.type.label) },
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Something else") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Done,
                    ),
                    // Done on the keyboard adds it, so a second account does not
                    // need a reach back to a button between every one.
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { add(typed); typed = "" },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Button(
            onClick = { onDone(drafts.toList()) },
            enabled = drafts.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (drafts.size == 1) "Start with 1 account" else "Start with ${drafts.size} accounts")
        }
    }
}
