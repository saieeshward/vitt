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
import ie.shoonya.vitt.money.AmountEntry
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.deviceCurrency
import ie.shoonya.vitt.ui.AmountKeypad
import ie.shoonya.vitt.ui.VittChip
import ie.shoonya.vitt.ui.theme.Vitt

/**
 * One account named during setup, before any of it is written to the log.
 *
 * [opening] is null until somebody types one, and null is not zero: an account
 * whose balance was never given is one whose figures are relative, while one
 * given as zero is an account that is genuinely empty. Writing the first as the
 * second would be the app inventing a fact about somebody's money.
 */
data class DraftAccount(
    val name: String,
    val currency: Currency,
    val opening: Money? = null,
)

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
    val drafts = remember { mutableStateListOf<DraftAccount>() }
    // Which account's keypad is open, or null for the list. Held here rather
    // than in the balances step so that stepping back and forth between naming
    // and balances does not lose it.
    var editing by remember { mutableStateOf<Int?>(null) }
    var onBalances by remember { mutableStateOf(false) }

    when {
        !onBalances -> NameStep(
            drafts = drafts,
            onNext = { onBalances = true },
            onSkip = onSkip,
            modifier = modifier,
        )
        editing != null -> {
            val index = editing ?: return
            val draft = drafts.getOrNull(index) ?: run { editing = null; return }
            BalanceStep(
                draft = draft,
                onBack = { editing = null },
                onSave = { amount ->
                    drafts[index] = draft.copy(opening = amount)
                    editing = null
                },
                modifier = modifier,
            )
        }
        else -> BalancesStep(
            drafts = drafts,
            onEdit = { editing = it },
            onBack = { onBalances = false },
            onDone = { onDone(drafts.toList()) },
            modifier = modifier,
        )
    }
}

/** Naming the accounts. The fast path, and the only step that is required. */
@Composable
private fun NameStep(
    drafts: MutableList<DraftAccount>,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The device region is a starting guess and never more than that: the
    // premise of the app is people whose money is in more than one currency, so
    // the second currency is the interesting one and it cannot be inferred.
    var currency by remember { mutableStateOf(deviceCurrency() ?: Currency.EUR) }
    var typed by remember { mutableStateOf("") }

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
                VittChip(
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
                        // Muted, not destructive. Nothing is written to the
                        // event log until Start, so this removes a line from a
                        // list rather than deleting anything, and a column of
                        // red words was shouting over the names the list exists
                        // to show.
                        TextButton(onClick = { drafts.remove(draft) }) {
                            Text(
                                "Remove",
                                style = Vitt.type.label,
                                color = Vitt.colors.inkMuted,
                            )
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
                        VittChip(
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
            onClick = onNext,
            enabled = drafts.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                // No count until there is one to give. "Next with 0 accounts"
                // reads as a broken sentence on the screen everybody sees first,
                // and the button is disabled there anyway.
                when (drafts.size) {
                    0 -> "Next"
                    1 -> "Next · 1 account"
                    else -> "Next · ${drafts.size} accounts"
                },
            )
        }
    }
}

/**
 * The balances, offered once and never demanded.
 *
 * This step exists because leaving it out was worse than the friction it
 * avoids. The reasoning against asking was sound in itself — a balance needs the
 * keypad, it often needs looking up in another app, and an account without one
 * is still a correct account — so the field was left to the edit sheet, one tap
 * away from any account row.
 *
 * One tap away is not the same as findable. Walking a fresh install as a new
 * user with four accounts, the app opened on four zeroes and said nothing about
 * them; the balances were reachable only by guessing that an account row was
 * tappable, and filling all four cost about forty taps that nothing had asked
 * for. A day-one app that shows every balance as zero does not read as a fast
 * setup. It reads as broken.
 *
 * So the question gets asked, once, where it belongs — and every part of it is
 * optional. No balance is required, the button says Start rather than Save, and
 * an account left blank is left blank rather than written as zero. The cost of
 * skipping is one tap; the cost of never being told is a person who thinks the
 * app does not work.
 */
@Composable
private fun BalancesStep(
    drafts: List<DraftAccount>,
    onEdit: (Int) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("What is in them?", style = Vitt.type.title, color = Vitt.colors.ink)
            TextButton(onClick = onBack) { Text("Back") }
        }
        Text(
            "Skip anything you would have to go and look up. An account with no " +
                "balance still works. Its figures start from where you are.",
            style = Vitt.type.label,
            color = Vitt.colors.inkMuted,
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Vitt.space.snug),
        ) {
            items(drafts.size) { index ->
                val draft = drafts[index]
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
                    TextButton(onClick = { onEdit(index) }) {
                        Text(
                            // The figure itself once there is one, so the list
                            // reads as the answers rather than as a row of
                            // identical buttons.
                            // Unsigned, as the account rows are. A leading plus
                            // on a balance reads as a change rather than a
                            // quantity: "+EUR 7,000.00" looks like money that
                            // just arrived.
                            draft.opening?.displayUnsigned() ?: "Add",
                            style = Vitt.type.label,
                        )
                    }
                }
            }
        }

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when (drafts.size) {
                    1 -> "Start with 1 account"
                    else -> "Start with ${drafts.size} accounts"
                },
            )
        }
    }
}

/** One account's opening balance, on the keypad. */
@Composable
private fun BalanceStep(
    draft: DraftAccount,
    onBack: () -> Unit,
    onSave: (Money) -> Unit,
    modifier: Modifier = Modifier,
) {
    var entry by remember(draft.name, draft.currency) {
        mutableStateOf(draft.opening?.let { AmountEntry.of(it) } ?: AmountEntry(currency = draft.currency))
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
            TextButton(onClick = onBack) { Text("Back") }
            Text(draft.name, style = Vitt.type.title, color = Vitt.colors.ink)
            // Enabled on a value, never on typed text: "0" and "0." are typed on
            // the way to "0.50" and must not save on their own.
            Button(enabled = entry.hasValue, onClick = { onSave(entry.money) }) { Text("Save") }
        }
        Text(
            "What is in the account now. Everything you record from here moves it.",
            style = Vitt.type.label,
            color = Vitt.colors.inkMuted,
        )
        AmountKeypad(entry = entry, onEntryChange = { entry = it })
    }
}
