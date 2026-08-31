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
    accounts: List<Account>,
    onSave: (
        amount: Money,
        merchant: String?,
        category: String?,
        accountId: String?,
        totalPaid: Money?,
    ) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var entry by remember { mutableStateOf(AmountEntry(currency = currencies.firstOrNull() ?: Currency.EUR)) }
    var kind by remember { mutableStateOf(EntryKind.Expense) }
    var category by remember { mutableStateOf<String?>(null) }
    var account by remember { mutableStateOf(accounts.firstOrNull()) }
    var split by remember { mutableStateOf(false) }
    // The second leg of a split: what was actually handed over, of which the
    // amount above is only the user's share.
    var paidEntry by remember { mutableStateOf(AmountEntry(currency = entry.currency)) }
    var onPaidStep by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // The keypad plus the category chips is taller than the sheet, and a
            // plain Column clips the overflow — the last row of categories was
            // simply unreachable. Scrolling keeps all of it available.
            .verticalScroll(rememberScrollState())
            .padding(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = if (onPaidStep) ({ onPaidStep = false }) else onCancel) {
                Text(if (onPaidStep) "Back" else "Cancel")
            }
            Text(
                if (onPaidStep) "Total paid" else "New",
                style = Vitt.type.title,
                color = Vitt.colors.ink,
            )
            if (split && !onPaidStep) {
                Button(enabled = !entry.isEmpty, onClick = { onPaidStep = true }) { Text("Next") }
            } else {
                Button(
                    enabled = !entry.isEmpty && (!split || paidEntry.money.minor >= entry.money.minor),
                    onClick = {
                        // The keypad holds a magnitude; the sign comes from the
                        // chosen direction, never guessed from the input.
                        val signed = when (kind) {
                            EntryKind.Expense -> Money(-entry.money.minor, entry.currency)
                            EntryKind.Income -> entry.money
                        }
                        val paid = if (split) {
                            Money(
                                if (signed.minor < 0) -paidEntry.money.minor else paidEntry.money.minor,
                                entry.currency,
                            )
                        } else {
                            null
                        }
                        onSave(signed, null, category, account?.id, paid)
                    },
                ) { Text("Save") }
            }
        }

        if (onPaidStep) {
            Text(
                "The whole bill, not your share. Your share stays " +
                    "${entry.display()}, and the difference is what someone owes you.",
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )
            AmountKeypad(entry = paidEntry, onEntryChange = { paidEntry = it })
            if (!paidEntry.isEmpty && paidEntry.money.minor < entry.money.minor) {
                Text(
                    "The total paid cannot be less than your own share.",
                    style = Vitt.type.label,
                    color = Vitt.colors.destructive,
                )
            }
            return@Column
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

        if (accounts.isNotEmpty()) {
            Text("Account", style = Vitt.type.caption, color = Vitt.colors.inkMuted)
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight),
                verticalArrangement = Arrangement.spacedBy(Vitt.space.tight),
            ) {
                accounts.forEach { a ->
                    FilterChip(
                        selected = account == a,
                        // Tapping the chosen account clears it. Capture often
                        // cannot tell which account paid, and forcing a guess is
                        // worse than leaving it unassigned and visible.
                        onClick = {
                            account = if (account == a) null else a
                            if (account != null) entry = entry.withCurrency(a.currency)
                        },
                        label = { Text(a.name, style = Vitt.type.label) },
                    )
                }
            }
        }

        AmountKeypad(entry = entry, onEntryChange = { entry = it })

        Row(horizontalArrangement = Arrangement.spacedBy(Vitt.space.snug)) {
            FilterChip(
                selected = split,
                onClick = {
                    split = !split
                    if (split) paidEntry = paidEntry.withCurrency(entry.currency)
                },
                label = { Text("Split this", style = Vitt.type.label) },
            )
        }

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
