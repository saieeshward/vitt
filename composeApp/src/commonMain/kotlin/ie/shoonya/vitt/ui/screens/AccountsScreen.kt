package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ie.shoonya.vitt.model.AccountBalance
import ie.shoonya.vitt.model.Transfer
import ie.shoonya.vitt.ui.theme.Vitt

/**
 * Where the money sits, and the moves between.
 *
 * A section rather than a screen, because it belongs under the currency cards on
 * the home tab: "which currencies" and "which accounts" are the same question at
 * two depths, and giving accounts their own tab would have cost the activity list
 * its place in a four-slot bar.
 *
 * Balances are grouped by currency because they are never summed. The grouping
 * makes the rule visible without a sentence explaining it: two headings means two
 * separate piles, and no arithmetic joins them.
 */
fun LazyListScope.accountsSection(
    balances: List<AccountBalance>,
    transfers: List<Transfer>,
    onAddAccount: () -> Unit,
    /** Opens one account for correction. The row is the only way in. */
    onEditAccount: (String) -> Unit,
    onTransfer: () -> Unit,
    accountName: (String) -> String,
    formatDay: (Int) -> String,
    /** Whether every account is listed, or only the first few with a "Show all". */
    showAllAccounts: Boolean,
    onToggleAccounts: () -> Unit,
    /** Whether archived accounts are listed after the live ones. */
    showArchived: Boolean,
    onToggleArchived: () -> Unit,
    showAllTransfers: Boolean,
    onToggleTransfers: () -> Unit,
    /** The hue of each currency, for the square beside its heading; null draws none. */
    currencyIndex: ((ie.shoonya.vitt.money.Currency) -> Int)? = null,
) {
    item {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Vitt.space.section),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Accounts", style = Vitt.type.title, color = Vitt.colors.ink)
            TextButton(onClick = onAddAccount) { Text("Add") }
        }
    }

    // `balances` arrives with the archived accounts in it, because this section
    // is the only place they can be brought back from. Everywhere else in the
    // app an archived account is simply gone.
    val live = balances.filterNot { it.account.archived }
    val archived = balances.filter { it.account.archived }

    if (live.isEmpty() && archived.isEmpty()) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Vitt.space.tight)) {
                Text("No accounts yet.", style = Vitt.type.title, color = Vitt.colors.ink)
                Text(
                    "One per currency. An account holding two is two accounts here.",
                    style = Vitt.type.label,
                    color = Vitt.colors.inkMuted,
                )
            }
        }
    } else {
        // Grouped, in the order the currencies first appeared, so a heading
        // does not jump position when a balance changes. Folded to the first
        // few until asked: twelve accounts is a screen on its own, and the
        // ones a person checks daily are the ones they listed first.
        val shown = if (showAllAccounts) live else live.take(ACCOUNTS_FOLDED)
        shown.groupBy { it.account.currency }.forEach { (currency, group) ->
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = Vitt.space.base, bottom = Vitt.space.hair),
                ) {
                    currencyIndex?.let { ie.shoonya.vitt.ui.CurrencyMark(it(currency), size = 6.dp) }
                    Text(
                        (if (currencyIndex != null) "  " else "") + currency.code,
                        style = Vitt.type.mono,
                        color = Vitt.colors.inkMuted,
                    )
                }
            }
            items(group) { balance ->
                AccountCard(balance, onEdit = { onEditAccount(balance.account.id) })
            }
        }
        if (live.size > ACCOUNTS_FOLDED) {
            item {
                TextButton(onClick = onToggleAccounts) {
                    Text(if (showAllAccounts) "Show fewer" else "Show all ${live.size}")
                }
            }
        }
        if (live.isNotEmpty()) {
            item {
                Text(
                    "Grouped by currency, never added up.",
                    style = Vitt.type.label,
                    color = Vitt.colors.inkMuted,
                )
            }
        }

        // Folded away by default and never counted in the fold above: archiving
        // something is a request for it to stop taking up room, and a list that
        // kept it in view would not have honoured that.
        if (archived.isNotEmpty()) {
            item {
                TextButton(onClick = onToggleArchived) {
                    Text(if (showArchived) "Hide archived" else "Archived (${archived.size})")
                }
            }
            if (showArchived) {
                items(archived) { balance ->
                    AccountCard(balance, onEdit = { onEditAccount(balance.account.id) })
                }
            }
        }
    }

    item {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Vitt.space.snug),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Transfers", style = Vitt.type.title, color = Vitt.colors.ink)
            TextButton(
                onClick = onTransfer,
                enabled = live.size >= 2,
            ) { Text("Move money") }
        }
    }

    if (transfers.isEmpty()) {
        item {
            Text(
                if (live.size >= 2) {
                    "Moving your own money is not spending, so no budget counts it."
                } else {
                    "Two accounts are needed before money can move."
                },
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )
        }
    } else {
        val shown = if (showAllTransfers) transfers else transfers.take(TRANSFERS_FOLDED)
        items(shown) { TransferRow(it, accountName, formatDay) }
        if (transfers.size > TRANSFERS_FOLDED) {
            item {
                TextButton(onClick = onToggleTransfers) {
                    Text(if (showAllTransfers) "Show fewer" else "Show all ${transfers.size}")
                }
            }
        }
    }
}

private const val ACCOUNTS_FOLDED = 4
private const val TRANSFERS_FOLDED = 2

/**
 * One account, one line.
 *
 * These were cards, three lines each, and at twelve accounts they were most of
 * the home screen. The name and the figure are what a person scans for; the
 * kind and the direction of the balance are the small print beside them.
 */
@Composable
private fun AccountCard(balance: AccountBalance, onEdit: () -> Unit) {
    val colors = Vitt.colors
    val archived = balance.account.archived
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The whole row, not a trailing pencil. There is one thing to do
            // with an account here, and a row that opens it is both the larger
            // target and the one that needs no icon explaining itself.
            .clickable(onClick = onEdit)
            // One ruled line per account, like the Activity page: the rule
            // is drawn, so the figures never shift for it.
            .heightIn(min = 48.dp)
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(colors.hairline, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            .padding(vertical = Vitt.space.tight)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                balance.account.name,
                style = Vitt.type.body,
                // Archived rows are readable but plainly set aside, so the live
                // accounts still lead when the list is expanded.
                color = if (archived) colors.inkMuted else colors.ink,
                maxLines = 1,
            )
            Text(
                balance.account.kind.label() + when {
                    archived -> " · archived"
                    balance.owed -> " · owed on this card"
                    balance.balance.minor < 0 -> " · overdrawn"
                    else -> ""
                },
                style = Vitt.type.caption,
                color = colors.inkFaint,
            )
        }
        // The magnitude, with the wording carrying the direction. A credit card
        // in debt reads "owed" rather than as a minus sign, because a negative
        // figure is a verdict and this is just a fact.
        Text(
            balance.display.displayUnsigned(),
            style = Vitt.type.money,
            color = if (archived) colors.inkMuted else colors.ink,
        )
    }
}

@Composable
private fun TransferRow(
    transfer: Transfer,
    accountName: (String) -> String,
    formatDay: (Int) -> String,
) {
    val colors = Vitt.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Vitt.radius.pill))
            .background(colors.surface)
            .padding(Vitt.space.base),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.hair),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "${accountName(transfer.fromAccountId)} → ${accountName(transfer.toAccountId)}",
                style = Vitt.type.label,
                color = colors.inkMuted,
            )
            Text(formatDay(transfer.day), style = Vitt.type.label, color = colors.inkFaint)
        }
        Text(
            "${transfer.sent.displayUnsigned()} → ${transfer.received.displayUnsigned()}",
            style = Vitt.type.money,
            color = colors.ink,
        )
        // The locked rate, shown because it is the point. It came from these two
        // amounts, so it cannot drift when the market does.
        transfer.rate?.toPlainString(4)?.let { rate ->
            Text(
                "at $rate ${transfer.received.currency.code} per ${transfer.sent.currency.code}",
                style = Vitt.type.label,
                color = colors.inkMuted,
            )
        }
        transfer.fee?.takeIf { it.minor != 0L }?.let {
            Text("${it.displayUnsigned()} in fees", style = Vitt.type.label, color = colors.inkMuted)
        }
        transfer.note?.let { Text(it, style = Vitt.type.label, color = colors.inkFaint) }
    }
}
