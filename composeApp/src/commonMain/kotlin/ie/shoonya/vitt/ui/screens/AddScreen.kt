package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import ie.shoonya.vitt.capture.Category
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
        category: Category?,
        accountId: String?,
        totalPaid: Money?,
    ) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var entry by remember { mutableStateOf(AmountEntry(currency = currencies.firstOrNull() ?: Currency.EUR)) }
    var kind by remember { mutableStateOf(EntryKind.Expense) }
    var category by remember { mutableStateOf<Category?>(null) }
    var account by remember { mutableStateOf(accounts.firstOrNull()) }
    var split by remember { mutableStateOf(false) }
    // The second leg of a split: what was actually handed over, of which the
    // amount above is only the user's share.
    var paidEntry by remember { mutableStateOf(AmountEntry(currency = entry.currency)) }
    var onPaidStep by remember { mutableStateOf(false) }

    // The scrolling body's viewport, derived from the window rather than a
    // guessed constant so it holds on a small phone and an iPad alike.
    val bodyMaxHeight = with(LocalDensity.current) {
        (LocalWindowInfo.current.containerSize.height * 0.72f).toDp()
    }

    // The header is pinned and only the body scrolls.
    //
    // This is a fix for losing an entry, not a tidiness change. The keypad plus
    // the category chips is taller than the sheet, so reaching a category means
    // scrolling down — and with the header inside the scroll, Save went with it.
    // Getting back to Save then meant dragging downward, which a
    // `ModalBottomSheet` reads as dismiss, so the sheet closed and the amount
    // was discarded with no warning. Found by logging a €3.60 coffee in the
    // simulator and then finding no such row in the database.
    //
    // Pinning the header breaks the chain at its first link: Save is always on
    // screen, so there is never a reason to scroll back up.
    Column(
        modifier = modifier.fillMaxWidth().padding(Vitt.space.loose),
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

        // Only the body scrolls. Everything below here can exceed the sheet;
        // the header above it must not move.
        //
        // The explicit height cap is load-bearing, not tidiness.
        //
        // A `verticalScroll` needs a bounded viewport or it simply grows to fit
        // its content and never scrolls, and `ModalBottomSheet` hands its
        // content an *unbounded* height — which also rules out `weight`, since
        // a Column cannot distribute infinite space. Both were tried here and
        // both left the category chips clipped off the bottom with no way to
        // reach them.
        //
        // So the cap comes from the window. It has to clear the whole keypad,
        // or the last digit row sits under the fold on first open and the most
        // common action in the app starts with a scroll — 0.56 did exactly
        // that. At 0.72 the keypad is fully visible and the scroll exists only
        // for the categories below it.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = bodyMaxHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
        ) {

        if (onPaidStep) {
            Text(
                "The whole bill, not your share. Yours stays ${entry.display()}.",
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
                    onClick = {
                        kind = k
                        // Income and Transfer leave the list when the direction
                        // flips, so a selection that is no longer offered has to
                        // go with it rather than persist invisibly.
                        if (category?.isSpending == (k == EntryKind.Income)) {
                            category = null
                        }
                    },
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
        CategoryChips(
            selected = category,
            income = kind == EntryKind.Income,
            onSelect = { category = it },
        )

        Text(
            "Two taps: type, then Save. The category is optional.",
            style = Vitt.type.label,
            color = Vitt.colors.inkMuted,
        )
        }

    }
}

/**
 * The locked taxonomy, as chips.
 *
 * Fourteen, from `PLAN.md` §6 — the list used to be a hand-written twelve here,
 * missing Income and Transfer and spelling two others differently. Reading it off
 * [Category] means it cannot drift again.
 *
 * Chips rather than a text field because the design has the category arriving
 * pre-filled from the learned rules; typing it was never the intended path, and a
 * text field here would summon the keyboard over the keypad.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(selected: Category?, income: Boolean, onSelect: (Category?) -> Unit) {
    // Direction is already chosen above, so offering the categories that
    // contradict it is just a way to record something incoherent.
    val offered = Category.entries.filter { if (income) !it.isSpending else it.isSpending }
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.tight),
    ) {
        offered.forEach { category ->
            FilterChip(
                selected = selected == category,
                // Tapping the chosen one clears it: the field is optional, so
                // there has to be a way back to none.
                onClick = { onSelect(if (selected == category) null else category) },
                label = { Text(category.label, style = Vitt.type.label) },
            )
        }
        }
}
