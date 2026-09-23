package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import ie.shoonya.vitt.capture.Category
import ie.shoonya.vitt.capture.Direction
import ie.shoonya.vitt.capture.ParsedTransaction
import ie.shoonya.vitt.model.Account
import ie.shoonya.vitt.model.SplitDraft
import ie.shoonya.vitt.model.Transaction
import ie.shoonya.vitt.model.Usual
import ie.shoonya.vitt.text.takeChars
import ie.shoonya.vitt.money.AmountEntry
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.money.Money
import ie.shoonya.vitt.ui.AmountKeypad
import ie.shoonya.vitt.ui.LocalWindowLayout
import ie.shoonya.vitt.ui.VittChip
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
    /** Who else was on a split. Empty for a split with nobody named yet. */
    val splitWith: Set<String> = emptySet(),
    /** Their parts when not equal; null for equal. */
    val shares: Map<String, Money>? = null,
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
    /**
     * A parse from shared text, filled in but never committed.
     *
     * The sheet opens on the amount rather than saving behind the user's back:
     * `AmountParser` would rather be unsure than guess a sign, and a guessed
     * sign turns a 40 refund into a 40 expense.
     */
    prefill: ParsedTransaction? = null,
    /**
     * Opens with "Split this" already on.
     *
     * Set when the sheet was reached from the People tab, which is the one
     * place in the app where somebody has said what they are doing before they
     * say what it cost.
     */
    startSplit: Boolean = false,
    onSave: (NewEntry) -> Unit,
    onCancel: () -> Unit,
    /**
     * Why the last Save did not take, or null.
     *
     * The sheet stays open when this is set. An entry that cannot be recorded
     * has to stay on screen with the figure still in it: the alternative is the
     * sheet closing on a transaction that was never written, which is the one
     * failure a money app does not get to have.
     */
    error: String? = null,
    /** People split with before, most recent first, offered as one-tap rows. */
    knownPeople: List<String> = emptyList(),
    /** What this person logs again and again; one tap fills the whole entry. */
    usuals: List<Usual> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val initialAccount = accounts.firstOrNull { it.id == lastAccountId } ?: accounts.firstOrNull()
    // Held as an id and resolved against the live list on every composition,
    // so an account that is renamed or archived by a sync while the sheet is
    // open drops out of the selection rather than being saved into blind.
    var accountId by remember { mutableStateOf(initialAccount?.id) }
    val account = accounts.firstOrNull { it.id == accountId }
    var entry by remember {
        val currency = prefill?.magnitude?.currency
            ?: initialAccount?.currency
            ?: currencies.first()
        mutableStateOf(
            // The magnitude, not the signed amount: the sign is carried by
            // `kind` below, and putting a negative into the keypad would show
            // the user a minus sign they never typed.
            prefill?.magnitude
                ?.let { AmountEntry.of(it) }
                ?: AmountEntry(currency = currency),
        )
    }
    var kind by remember {
        mutableStateOf(
            // UNKNOWN stays Expense and the user resolves it. `isUsable` is
            // false in that case, which is what makes the sheet open rather
            // than the entry save.
            if (prefill?.direction == Direction.INFLOW) EntryKind.Income else EntryKind.Expense,
        )
    }
    var category by remember { mutableStateOf<Category?>(null) }
    var allCategories by remember { mutableStateOf(false) }
    var split by remember { mutableStateOf(startSplit) }
    // The merchant goes in the note, which is where a shared bank alert's
    // "TESCO STORES 3421 DUBLIN IE" belongs: it is what the entry was, and the
    // categoriser already learns from it.
    var note by remember { mutableStateOf(prefill?.merchant.orEmpty()) }
    // A split starts from the bill, the way it is read off a receipt, then
    // says who it was for on one screen, the way Splitwise and Tricount do.
    // The draft follows the bill as it is typed, and keeps who and any typed
    // parts across a trip back to change the bill.
    var draft by remember { mutableStateOf(SplitDraft.of(entry.money)) }
    val splitDraft = draft.withTotal(entry.money)
    var newName by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(Step.Main) }

    // The scrolling body's viewport, derived from the window rather than a
    // guessed constant so it holds on a small phone and an iPad alike.
    // Whose part the pinned keypad is typing on the split step, if anyone's.
    var partFocus by remember { mutableStateOf<String?>(null) }
    // All the height the sheet has, less its own chrome and the part keypad
    // when it is pinned below: the body fills the screen rather than stopping
    // at a fraction of it with the foot left empty.
    val pinned = step == Step.Share && partFocus != null
    val bodyMaxHeight = ie.shoonya.vitt.ui.sheetBodyMaxHeight(
        ie.shoonya.vitt.ui.SHEET_CHROME + if (pinned) ie.shoonya.vitt.ui.PART_KEYPAD else 0.dp,
    )

    // On value, not on text: "0" and "0." are typed on the way to "0.50" and
    // must not be saveable on their own.
    val canSave = entry.hasValue && (!split || splitDraft.isValid)
    val save = {
        // The keypad holds a magnitude; the sign comes from the chosen
        // direction, never guessed from the input. On a split the amount
        // recorded is the user's share, which is what the budget counts, and the
        // bill goes beside it.
        val sign = if (kind == EntryKind.Expense) -1 else 1
        val signed = Money(sign * (if (split) splitDraft.yours else entry.money).minor, entry.currency)
        val paid = if (split) Money(sign * entry.money.minor, entry.currency) else null
        onSave(
            NewEntry(
                amount = signed,
                category = category,
                accountId = account?.id,
                totalPaid = paid,
                note = note.trim().ifEmpty { null },
                splitWith = if (split) splitDraft.people.toSet() else emptySet(),
                shares = if (split) splitDraft.othersToSave else null,
            ),
        )
    }

    val bodyScroll = rememberScrollState()

    // The header is pinned and only the body scrolls.
    //
    // This is a fix for losing an entry, not a tidiness change. With the header
    // inside the scroll, reaching the bottom of the body took Save with it, and
    // dragging back up read to the `ModalBottomSheet` as dismiss: the sheet
    // closed and the amount was discarded with no warning. Found by logging a
    // €3.60 coffee in the simulator and then finding no such row in the
    // database. Pinning the header breaks the chain at its first link.
    Column(
        modifier = modifier
            .fillMaxWidth()
            // With a keyboard: Return saves when Save would, and Escape
            // backs out, one step at a time as the button beside it does.
            // Preview, so the keypad's own key handling does not swallow them.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter -> {
                        val atSave = step == Step.Share || (step == Step.Main && !split)
                        if (atSave && canSave) { save(); true } else false
                    }
                    Key.Escape -> {
                        when (step) {
                            Step.Main -> onCancel()
                            Step.NewPerson -> { newName = ""; step = Step.Share }
                            else -> step = Step.Main
                        }
                        true
                    }
                    else -> false
                }
            }
            .padding(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = when (step) {
                    Step.Main -> onCancel
                    Step.NewPerson -> ({ newName = ""; step = Step.Share })
                    else -> ({ step = Step.Main })
                },
            ) {
                Text(if (step != Step.Main) "Back" else "Cancel")
            }
            Text(
                when (step) {
                    Step.Main -> "New"
                    Step.Share -> "Split"
                    Step.NewPerson -> "Someone new"
                    Step.Note -> "Note"
                },
                style = Vitt.type.title,
                color = Vitt.colors.ink,
            )
            when {
                step == Step.Note -> Button(onClick = { step = Step.Main }) { Text("Done") }
                step == Step.NewPerson -> Button(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        draft = draft.toggle(newName)
                        newName = ""
                        step = Step.Share
                    },
                ) { Text("Add") }
                split && step == Step.Main ->
                    Button(enabled = entry.hasValue, onClick = { step = Step.Share }) { Text("Next") }
                else -> Button(enabled = canSave, onClick = save) { Text("Save") }
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
                .verticalScroll(bodyScroll),
            verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
        ) {
            when (step) {
                Step.Share -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Bill", style = Vitt.type.label, color = Vitt.colors.inkMuted)
                        Text(entry.money.displayUnsigned(), style = Vitt.type.money, color = Vitt.colors.ink)
                    }
                    SplitEditor(
                        draft = splitDraft,
                        onDraft = { draft = it },
                        suggestions = knownPeople,
                        onNewPerson = { step = Step.NewPerson },
                        focus = partFocus,
                        onFocus = { partFocus = it },
                    )
                }

                // Naming somebody needs the system keyboard, which cannot share
                // the sheet with the keypad, so it is a step of its own like the
                // note.
                Step.NewPerson -> {
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focus.requestFocus() }
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("Email or name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (newName.isNotBlank()) draft = draft.toggle(newName)
                            newName = ""
                            step = Step.Share
                        }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                    Text(
                        "A label for your own records. Nothing is sent.",
                        style = Vitt.type.label,
                        color = Vitt.colors.inkMuted,
                    )
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
                    // A repeat is the usual, then Save: two taps for the coffee
                    // logged every morning. It fills the sheet and stops there,
                    // so a price that changed is one keypad edit away.
                    val usualChips: @Composable () -> Unit = {
                        if (usuals.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight),
                            ) {
                                usuals.forEach { u ->
                                    VittChip(
                                        selected = note == u.label && entry.money == u.amount.abs(),
                                        onClick = {
                                            kind = if (u.amount.isInflow) EntryKind.Income else EntryKind.Expense
                                            entry = AmountEntry.of(u.amount.abs())
                                            accountId = (
                                                accounts.firstOrNull { it.id == u.accountId && it.currency == u.amount.currency }
                                                    ?: accounts.firstOrNull { it.currency == u.amount.currency }
                                                )?.id
                                            category = u.category?.takeIf { it.isPickable && it.isSpending == u.amount.isOutflow }
                                            note = u.label
                                            split = false
                                        },
                                        label = { Text("${u.label} ${u.amount.abs().displayUnsigned()}", style = Vitt.type.label, maxLines = 1) },
                                    )
                                }
                            }
                        }
                    }
                    val kindChips: @Composable () -> Unit = {
                        Row(horizontalArrangement = Arrangement.spacedBy(Vitt.space.snug)) {
                            EntryKind.entries.forEach { k ->
                                VittChip(
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
                    }
                    val keypad: @Composable () -> Unit = {
                        if (split) {
                            Text("The whole bill", style = Vitt.type.label, color = Vitt.colors.inkMuted)
                        }
                        AmountKeypad(entry = entry, onEntryChange = { entry = it })
                    }
                    val choices: @Composable () -> Unit = {
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
                                    VittChip(
                                        selected = entry.currency == c,
                                        onClick = {
                                            // Re-tapping the current currency changes
                                            // nothing, so an account the person just
                                            // cleared is not quietly picked again.
                                            if (c != entry.currency) {
                                                entry = entry.withCurrency(c)
                                                draft = SplitDraft.of(Money(0, c), draft.people)
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
                                    VittChip(
                                        selected = accountId == a.id,
                                        // Tapping the chosen account clears it. Capture
                                        // often cannot tell which account paid, and a
                                        // forced guess is worse than a visible blank.
                                        onClick = { accountId = if (accountId == a.id) null else a.id },
                                        label = { Text(a.name, style = Vitt.type.label) },
                                    )
                                }
                            }
                            // Said out loud, because the blank is easy to arrive at
                            // by accident: the chips are a toggle, so one stray tap
                            // on the chosen account clears it, and nothing else on
                            // this sheet changes when it does. Saving without an
                            // account is still allowed — capture often cannot tell
                            // which one paid — but it should never be a surprise.
                            if (accountId == null) {
                                Text(
                                    "No account picked. The entry is still recorded; " +
                                        "no balance moves until you pick one.",
                                    style = Vitt.type.label,
                                    color = Vitt.colors.inkMuted,
                                )
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
                            VittChip(
                                selected = split,
                                onClick = {
                                    split = !split
                                },
                                label = { Text("Split this", style = Vitt.type.label) },
                            )
                            VittChip(
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

                        if (error != null) {
                            Text(
                                error,
                                style = Vitt.type.body,
                                color = Vitt.colors.destructive,
                                modifier = Modifier.semantics {
                                    liveRegion = LiveRegionMode.Assertive
                                },
                            )
                        }

                        Text(
                            "Type the amount, then Save. Everything else is optional.",
                            style = Vitt.type.label,
                            color = Vitt.colors.inkMuted,
                        )
                    }
                    // A phone on its side: the keypad and the choices side by side,
                    // because stacked they need twice the height the window has.
                    if (LocalWindowLayout.current.shortHeight) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Vitt.space.section)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Vitt.space.base)) {
                                kindChips()
                                usualChips()
                                choices()
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Vitt.space.base)) {
                                keypad()
                            }
                        }
                    } else {
                        kindChips()
                        usualChips()
                        keypad()
                        choices()
                    }
                }
            }
        }

        // Pinned under the body, not at its end: see SplitPartKeypad.
        if (step == Step.Share) {
            partFocus?.let { who ->
                SplitPartKeypad(
                    draft = splitDraft,
                    who = who,
                    onDraft = { draft = it },
                    onDone = { partFocus = null },
                )
            }
        }
    }
}

private enum class Step { Main, Share, NewPerson, Note }

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
            VittChip(
                selected = selected == category,
                // Tapping the chosen one clears it: the field is optional, so
                // there has to be a way back to none.
                onClick = { onSelect(if (selected == category) null else category) },
                label = { Text(category.label, style = Vitt.type.label) },
            )
        }
        if (shown.size < all.size) {
            VittChip(
                selected = false,
                onClick = onShowAll,
                label = { Text("More", style = Vitt.type.label) },
            )
        }
    }
}
