package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ie.shoonya.vitt.model.Transaction
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.ui.theme.Vitt

/**
 * Every transaction, grouped by day, newest first.
 *
 * Dense by design: one loud number belongs on the Ledgers card, not on every row
 * here. Rows carry their own currency symbol because there is no default
 * currency in this app.
 */
@Composable
fun ActivityScreen(
    days: List<Pair<Int, List<Transaction>>>,
    currencyIndex: (Currency) -> Int,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.tight),
    ) {
        item { Text("Activity", style = Vitt.type.title, color = Vitt.colors.ink) }

        if (days.isEmpty()) {
            item {
                Text(
                    "Nothing here yet.",
                    style = Vitt.type.body,
                    color = Vitt.colors.inkMuted,
                    modifier = Modifier.padding(top = Vitt.space.tight),
                )
            }
        }

        days.forEach { (day, transactions) ->
            item {
                Text(
                    formatDay(day),
                    style = Vitt.type.caption,
                    color = Vitt.colors.inkMuted,
                    modifier = Modifier.padding(top = Vitt.space.base),
                )
            }
            items(transactions.size) { i ->
                TransactionRow(transactions[i], currencyIndex)
            }
        }
    }
}

@Composable
private fun TransactionRow(txn: Transaction, currencyIndex: (Currency) -> Int) {
    val colors = Vitt.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Vitt.space.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(6.dp).clip(CircleShape)
                .background(colors.currency(currencyIndex(txn.amount.currency))),
        )
        Column(
            modifier = Modifier.padding(start = Vitt.space.snug).fillMaxWidth(0.62f),
        ) {
            Text(
                txn.merchant ?: txn.category ?: "—",
                style = Vitt.type.body,
                color = colors.ink,
                maxLines = 1,
            )
            val subtitle = buildList {
                txn.category?.let { add(it) }
                if (txn.isSplit) add("your share")
            }.joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = Vitt.type.caption, color = colors.inkMuted, maxLines = 1)
            }
        }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                txn.amount.display(),
                style = Vitt.type.money,
                // Spending is plain text; only income takes colour. Colouring an
                // expense would be the app passing judgement on it.
                color = if (txn.amount.isInflow) colors.accentSoft else colors.ink,
            )
        }
    }
}

/**
 * A day, as a person reads it.
 *
 * Hand-rolled from the epoch-day integer rather than pulled from a date library:
 * the model stores a day with no time and no zone, and formatting it must not
 * quietly reintroduce either.
 */
internal fun formatDay(epochDay: Int): String {
    var days = epochDay.toLong() + 719_468L
    val era = (if (days >= 0) days else days - 146_096L) / 146_097L
    val doe = days - era * 146_097L
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146_096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val year = if (m <= 2) y + 1 else y
    val month = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )[(m - 1).toInt()]
    return "$d $month $year"
}
