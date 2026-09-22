package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import ie.shoonya.vitt.capture.CsvDate
import ie.shoonya.vitt.capture.CsvPlan
import ie.shoonya.vitt.model.Account
import ie.shoonya.vitt.ui.theme.Vitt

/**
 * Bringing a bank export in, with everything shown before anything is written.
 *
 * The order of this screen is the whole design, because a CSV import is the
 * least undoable thing the app can do. The ledger is an append-only event log,
 * so five hundred rows imported by mistake are five hundred deletions, and every
 * one of them has already been pushed to the person's own spreadsheet. So the
 * file is read, resolved and *shown* first, and the button that writes is the
 * last thing reachable.
 *
 * Two questions get asked before that button lights up, and neither is
 * decoration.
 *
 * **Which way round are the dates.** Only when the file cannot answer it
 * itself. `03/04` is the third of April here and the fourth of March in the
 * United States, and choosing wrong silently moves every entry a month.
 *
 * **Which account this is.** An import with no account lands as a pile of
 * unassigned entries, which is exactly the mess first-run setup was built to
 * avoid.
 */
@Composable
fun ImportSheet(
    plan: CsvPlan?,
    accounts: List<Account>,
    /**
     * The account chosen, held by the caller rather than here.
     *
     * The file has to be parsed *in* a currency, because a minor unit is not a
     * fixed thing: "12.50" is 1250 minor in euro and meaningless in yen, which
     * has no decimals at all. So changing the account re-reads the file rather
     * than relabelling numbers that were scaled for a different currency.
     */
    accountId: String?,
    onAccountChange: (String) -> Unit,
    /** Null while a file is being read, so the sheet can say so. */
    busy: Boolean,
    onPickFile: () -> Unit,
    /** The user answering an ambiguous file. Re-plans without re-reading it. */
    onDateOrder: (CsvDate.Order) -> Unit,
    onImport: (accountId: String, rows: List<ie.shoonya.vitt.capture.PlannedRow>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSkipped by remember(plan) { mutableStateOf(false) }

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
            Text("Import", style = Vitt.type.title, color = Vitt.colors.ink)
            // Deliberately not a button until there is something to import and
            // somewhere to put it.
            Button(
                enabled = plan != null && accountId != null && plan.importable.isNotEmpty(),
                onClick = {
                    val id = accountId ?: return@Button
                    onImport(id, plan?.importable.orEmpty())
                },
            ) { Text("Import") }
        }

        if (busy) {
            Text("Reading the file…", style = Vitt.type.body, color = Vitt.colors.inkMuted)
            return@Column
        }

        if (plan == null) {
            Text(
                "Bring in a bank export and start with your history already there.",
                style = Vitt.type.body,
                color = Vitt.colors.ink,
            )
            Text(
                "A CSV from your bank. Nothing leaves your phone, and nothing is " +
                    "saved until you have seen what it found.",
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )
            Button(onClick = onPickFile) { Text("Choose a file") }
            return@Column
        }

        // The ambiguous case comes first, because until it is answered every
        // other number on this screen is wrong.
        if (plan.needsDateOrder) {
            Text("Which way round are these dates?", style = Vitt.type.body, color = Vitt.colors.ink)
            Text(
                "This file uses dates like 03/04 and nothing in it says which part " +
                    "is the day. Picking wrong would move every entry by a month.",
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight)) {
                FilterChip(
                    selected = false,
                    onClick = { onDateOrder(CsvDate.Order.DAY_FIRST) },
                    label = { Text("03/04 is 3 April", style = Vitt.type.label) },
                )
                FilterChip(
                    selected = false,
                    onClick = { onDateOrder(CsvDate.Order.MONTH_FIRST) },
                    label = { Text("03/04 is 4 March", style = Vitt.type.label) },
                )
            }
            return@Column
        }

        Text(
            "${plan.importable.size} entries",
            style = Vitt.type.money,
            color = Vitt.colors.ink,
        )

        // The count worth reading is the one that says what will be lost. A
        // person accepts "412 entries" without looking; "nine skipped" is the
        // line that makes them look.
        if (plan.skipped.isNotEmpty()) {
            TextButton(onClick = { showSkipped = !showSkipped }) {
                Text(
                    if (showSkipped) "Hide the ${plan.skipped.size} skipped"
                    else "${plan.skipped.size} rows will be skipped",
                )
            }
            if (showSkipped) {
                plan.skipped.take(SKIPPED_SHOWN).forEach { row ->
                    Text(
                        "Line ${row.lineNumber}: ${row.problems.joinToString(", ")}",
                        style = Vitt.type.label,
                        color = Vitt.colors.inkMuted,
                    )
                }
                if (plan.skipped.size > SKIPPED_SHOWN) {
                    Text(
                        "and ${plan.skipped.size - SKIPPED_SHOWN} more",
                        style = Vitt.type.label,
                        color = Vitt.colors.inkFaint,
                    )
                }
            }
        }

        if (plan.importable.isEmpty()) {
            Text(
                "Nothing here can be imported. Try a different export, or a CSV " +
                    "with a date and an amount column.",
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )
            return@Column
        }

        Text("Into which account?", style = Vitt.type.caption, color = Vitt.colors.inkMuted)
        if (accounts.isEmpty()) {
            // Importing into nothing would produce a pile of unassigned entries,
            // which is the mess first-run setup exists to prevent.
            Text(
                "Add an account first. An import needs somewhere to land.",
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )
        } else {
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight),
                verticalArrangement = Arrangement.spacedBy(Vitt.space.tight),
            ) {
                accounts.forEach { account ->
                    FilterChip(
                        selected = account.id == accountId,
                        onClick = { onAccountChange(account.id) },
                        label = {
                            Text("${account.name} · ${account.currency.code}", style = Vitt.type.label)
                        },
                    )
                }
            }
            // The currency is the account's, not the file's: the amounts are
            // integers in minor units and nothing here converts anything.
            Text(
                "Every row lands in this account, in its currency.",
                style = Vitt.type.label,
                color = Vitt.colors.inkFaint,
            )
        }
    }
}

/** Enough to see the shape of the problem without turning the sheet into a log. */
private const val SKIPPED_SHOWN = 6
