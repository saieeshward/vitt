package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
    onTransfer: () -> Unit,
    accountName: (String) -> String,
    formatDay: (Int) -> String,
    /** Whether every account is listed, or only the first few with a "Show all". */
    showAllAccounts: Boolean,
    onToggleAccounts: () -> Unit,
    showAllTransfers: Boolean,
    onToggleTransfers: () -> Unit,
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

    if (balances.isEmpty()) {
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
        val shown = if (showAllAccounts) balances else balances.take(ACCOUNTS_FOLDED)
        shown.groupBy { it.account.currency }.forEach { (currency, group) ->
            item {
                Text(
                    currency.code,
                    style = Vitt.type.caption,
                    color = Vitt.colors.inkMuted,
                    modifier = Modifier.padding(top = Vitt.space.snug),
                )
            }
            items(group) { AccountCard(it) }
        }
        if (balances.size > ACCOUNTS_FOLDED) {
            item {
                TextButton(onClick = onToggleAccounts) {
                    Text(if (showAllAccounts) "Show fewer" else "Show all ${balances.size}")
                }
            }
        }
        item {
            Text(
                "Grouped by currency, never added up.",
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )
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
                enabled = balances.size >= 2,
            ) { Text("Move money") }
        }
    }

    if (transfers.isEmpty()) {
        item {
            Text(
                if (balances.size >= 2) {
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
private fun AccountCard(balance: AccountBalance) {
    val colors = Vitt.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Vitt.space.snug)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(balance.account.name, style = Vitt.type.body, color = colors.ink, maxLines = 1)
            Text(
                balance.account.kind.label() + when {
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
            color = colors.ink,
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
