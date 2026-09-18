package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import ie.shoonya.vitt.capture.Category
import ie.shoonya.vitt.model.Account
import ie.shoonya.vitt.model.Transaction
import ie.shoonya.vitt.text.takeChars
import ie.shoonya.vitt.money.AmountEntry
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.ui.AmountKeypad
import ie.shoonya.vitt.ui.theme.Vitt

/** What the user is recording. Direction is chosen, never inferred. */
enum class EntryKind { Expense, Income }

/** Everything the add screen hands back. One object, so a new field is one edit. */
data class NewEntry(
    val amount: Money,
    val category: Category?,
    val accountId: String?,
    val totalPaid: Money?,
    val note: String?,
)

/**
 * Two taps to log: type the amount, then Save.
 *
 * Everything else is optional and pre-guessed. Capture is the only thing that
 * happens daily, so it owns the shortest path — every extra required field is a
 * future uninstall.
 *
 * The order on the screen is the order of certainty. The amount is the one
 * thing the person knows for sure, so the keypad is first and open. Then the
 * currency, and under it only that currency's accounts, so a currency with one
 * account is a single tap and twelve accounts never become six rows of chips.
 * The category row shows the few this person actually uses; the other nine sit
 * behind "More". Split and Note are last, because they are rare.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddScreen(
    currencies: List<Currency>,
    accounts: List<Account>,
    /** Most-used spending categories, for the first row of chips. */
    frequentSpending: List<Category>,
    /** Most-used income categories. */
    frequentIncome: List<Category>,
    /** The account the last entry went into, pre-selected. */
    lastAccountId: String?,
    onSave: (NewEntry) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialAccount = accounts.firstOrNull { it.id == lastAccountId } ?: accounts.firstOrNull()
    // Held as an id and resolved against the live list on every composition,
    // so an account that is renamed or archived by a sync while the sheet is
    // open drops out of the selection rather than being saved into blind.
    var accountId by remember { mutableStateOf(initialAccount?.id) }
    val account = accounts.firstOrNull { it.id == accountId }
    var entry by remember {
        mutableStateOf(
            AmountEntry(currency = initialAccount?.currency ?: currencies.first()),
        )
    }
    var kind by remember { mutableStateOf(EntryKind.Expense) }
    var category by remember { mutableStateOf<Category?>(null) }
    var allCategories by remember { mutableStateOf(false) }
    var split by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    // The second leg of a split: what was actually handed over, of which the
    // amount above is only the user's share.
    var paidEntry by remember { mutableStateOf(AmountEntry(currency = entry.currency)) }
    var step by remember { mutableStateOf(Step.Main) }

    // The scrolling body's viewport, derived from the window rather than a
    // guessed constant so it holds on a small phone and an iPad alike.
    val bodyMaxHeight = with(LocalDensity.current) {
        (LocalWindowInfo.current.containerSize.height * 0.72f).toDp()
    }

    // The header is pinned and only the body scrolls.
    //
    // This is a fix for losing an entry, not a tidiness change. With the header
    // inside the scroll, reaching the bottom of the body took Save with it, and
    // dragging back up read to the `ModalBottomSheet` as dismiss: the sheet
    // closed and the amount was discarded with no warning. Found by logging a
    // €3.60 coffee in the simulator and then finding no such row in the
    // database. Pinning the header breaks the chain at its first link.
    Column(
        modifier = modifier.fillMaxWidth().padding(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = if (step != Step.Main) ({ step = Step.Main }) else onCancel) {
                Text(if (step != Step.Main) "Back" else "Cancel")
            }
            Text(
                when (step) {
                    Step.Main -> "New"
                    Step.Paid -> "Total paid"
                    Step.Note -> "Note"
                },
                style = Vitt.type.title,
                color = Vitt.colors.ink,
            )
            when {
                step == Step.Note -> Button(onClick = { step = Step.Main }) { Text("Done") }
                split && step == Step.Main ->
                    Button(enabled = entry.hasValue, onClick = { step = Step.Paid }) { Text("Next") }
                else -> Button(
                    // On value, not on text: "0" and "0." are typed on the way
                    // to "0.50" and must not be saveable on their own.
                    enabled = entry.hasValue &&
                        (!split || (paidEntry.hasValue && paidEntry.money.minor >= entry.money.minor)),
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
                        onSave(
                            NewEntry(
                                amount = signed,
                                category = category,
                                accountId = account?.id,
                                totalPaid = paid,
                                note = note.trim().ifEmpty { null },
                            ),
                        )
                    },
                ) { Text("Save") }
            }
        }

        // Only the body scrolls. Everything below here can exceed the sheet;
        // the header above it must not move.
        //
        // The explicit height cap is load-bearing. A `verticalScroll` needs a
        // bounded viewport or it grows to fit and never scrolls, and
        // `ModalBottomSheet` hands its content an *unbounded* height, which
        // also rules out `weight`. At 0.72 of the window the whole keypad is
        // visible on first open and the scroll exists only for what is below.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = bodyMaxHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
        ) {
            when (step) {
                Step.Paid -> {
                    Text(
                        "The whole bill, not your share. Yours stays ${entry.display(full = true)}.",
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
                }

                // The note has its own step because it needs the system
                // keyboard, and in a bottom sheet that keyboard covers the lower
                // half of the keypad: 7, 8, 9, 0 and delete become unreachable
                // and the amount cannot be finished. Here there is no keypad to
                // cover, so the two never share a screen.
                Step.Note -> {
                    // Focused on arrival: the step exists only to type, so a
                    // tap on the field first would be a tap for nothing.
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focus.requestFocus() }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.takeChars(Transaction.MAX_NOTE) },
                        placeholder = { Text("Birthday dinner, deposit back…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { step = Step.Main }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                    Text(
                        "A few words for later. Shown on the entry, never used to guess anything.",
                        style = Vitt.type.label,
                        color = Vitt.colors.inkMuted,
                    )
                }

                Step.Main -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(Vitt.space.snug)) {
                        EntryKind.entries.forEach { k ->
                            FilterChip(
                                selected = kind == k,
                                onClick = {
                                    kind = k
                                    // A selection no longer offered has to go
                                    // rather than persist invisibly.
                                    if (category?.isSpending == (k == EntryKind.Income)) category = null
                                },
                                label = { Text(k.name) },
                            )
                        }
                    }

                    AmountKeypad(entry = entry, onEntryChange = { entry = it })

                    // Currency first, then only that currency's accounts.
                    //
                    // The first cut listed every account and let the account
                    // carry the currency, which read well with three accounts
                    // and badly with twelve: six rows of chips pushed the
                    // category off the screen. A currency row is one line
                    // however many accounts there are, and under it most
                    // people have one or two. Picking a currency picks its
                    // only account, so the common case is still one tap.
                    // Account-backed currencies first, then any the ledger has
                    // seen without an account (imported rows, entries from before
                    // the first account). Adding one account must not take away
                    // a currency the zero-account state offered.
                    val shownCurrencies = (accounts.map { it.currency } + currencies).distinct()
                    if (shownCurrencies.size > 1) {
                        // Wraps, because six currencies do not fit one row and
                        // a Row squeezed the sixth into a vertical column.
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight),
                            verticalArrangement = Arrangement.spacedBy(Vitt.space.tight),
                        ) {
                            shownCurrencies.forEach { c ->
                                FilterChip(
                                    selected = entry.currency == c,
                                    onClick = {
                                        // Re-tapping the current currency changes
                                        // nothing, so an account the person just
                                        // cleared is not quietly picked again.
                                        if (c != entry.currency) {
                                            entry = entry.withCurrency(c)
                                            paidEntry = paidEntry.withCurrency(c)
                                            accountId = accounts.firstOrNull { it.currency == c }?.id
                                        }
                                    },
                                    label = { Text(c.code, style = Vitt.type.label) },
                                )
                            }
                        }
                    }
                    val inCurrency = accounts.filter { it.currency == entry.currency }
                    if (inCurrency.isNotEmpty()) {
                        Text(
                            if (kind == EntryKind.Income) "Into" else "From",
                            style = Vitt.type.caption,
                            color = Vitt.colors.inkMuted,
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight),
                            verticalArrangement = Arrangement.spacedBy(Vitt.space.tight),
                        ) {
                            inCurrency.forEach { a ->
                                FilterChip(
                                    selected = accountId == a.id,
                                    // Tapping the chosen account clears it. Capture
                                    // often cannot tell which account paid, and a
                                    // forced guess is worse than a visible blank.
                                    onClick = { accountId = if (accountId == a.id) null else a.id },
                                    label = { Text(a.name, style = Vitt.type.label) },
                                )
                            }
                        }
                    }

                    Text("Category", style = Vitt.type.caption, color = Vitt.colors.inkMuted)
                    CategoryChips(
                        selected = category,
                        frequent = if (kind == EntryKind.Income) frequentIncome else frequentSpending,
                        income = kind == EntryKind.Income,
                        showAll = allCategories,
                        onShowAll = { allCategories = true },
                        onSelect = { category = it },
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(Vitt.space.snug)) {
                        FilterChip(
                            selected = split,
                            onClick = {
                                split = !split
                                if (split) paidEntry = paidEntry.withCurrency(entry.currency)
                            },
                            label = { Text("Split this", style = Vitt.type.label) },
                        )
                        FilterChip(
                            selected = note.isNotBlank(),
                            onClick = { step = Step.Note },
                            label = {
                                Text(
                                    note.trim().ifEmpty { "Add a note" },
                                    style = Vitt.type.label,
                                    maxLines = 1,
                                )
                            },
                        )
                    }

                    Text(
                        "Type the amount, then Save. Everything else is optional.",
                        style = Vitt.type.label,
                        color = Vitt.colors.inkMuted,
                    )
                }
            }
        }
    }
}

private enum class Step { Main, Paid, Note }

/**
 * The locked taxonomy, as chips: the few this person uses, then "More".
 *
 * Fourteen categories from `PLAN.md` §6, read off [Category] so the list cannot
 * drift. Shown in full they were three rows below the keypad and the most
 * common action in the app ended in a scroll, so the first row is the ones the
 * person actually picks and the rest unfold on request. A category already
 * chosen is always visible, wherever it ranks.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(
    selected: Category?,
    frequent: List<Category>,
    income: Boolean,
    showAll: Boolean,
    onShowAll: () -> Unit,
    onSelect: (Category?) -> Unit,
) {
    // Direction is already chosen above, so offering the categories that
    // contradict it is just a way to record something incoherent.
    val all = Category.entries.filter { it.isPickable && it.isSpending != income }
    val shown = when {
        showAll || all.size <= frequent.size + 1 -> all
        selected != null && selected !in frequent -> frequent + selected
        else -> frequent
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.tight),
    ) {
        shown.forEach { category ->
            FilterChip(
                selected = selected == category,
                // Tapping the chosen one clears it: the field is optional, so
                // there has to be a way back to none.
                onClick = { onSelect(if (selected == category) null else category) },
                label = { Text(category.label, style = Vitt.type.label) },
            )
        }
        if (shown.size < all.size) {
            FilterChip(
                selected = false,
                onClick = onShowAll,
                label = { Text("More", style = Vitt.type.label) },
            )
        }
    }
}
