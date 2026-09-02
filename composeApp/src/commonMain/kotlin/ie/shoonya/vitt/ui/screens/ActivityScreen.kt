package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import ie.shoonya.vitt.capture.CategorySource
import ie.shoonya.vitt.model.Transaction
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.time.Civil
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
    onEdit: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.hair),
    ) {
        item { Text("Activity", style = Vitt.type.display, color = Vitt.colors.ink) }

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
                TransactionRow(transactions[i], currencyIndex, onEdit)
            }
        }
    }
}

@Composable
private fun TransactionRow(
    txn: Transaction,
    currencyIndex: (Currency) -> Int,
    onEdit: (Transaction) -> Unit,
) {
    val colors = Vitt.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit(txn) }
            .padding(vertical = Vitt.space.hair),
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
                txn.merchantLabel ?: txn.categoryOrNull?.label ?: "—",
                style = Vitt.type.body,
                color = colors.ink,
                maxLines = 1,
            )
            val subtitle = buildList {
                // The label, not the stored code — "groceries" reads as a
                // database field.
                txn.categoryOrNull?.let { add(it.label) } ?: txn.category?.let { add(it) }
                // Which tier produced it. §6 calls this the single best
                // debugging affordance in the system, and it costs one word.
                txn.categorySource?.let { if (it != CategorySource.MANUAL) add(it.code) }
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
 * The calendar arithmetic lives in `time/Civil.kt`; this file used to carry its
 * own copy, which was the third in the repo.
 */
internal fun formatDay(epochDay: Int): String {
    val (year, month, day) = Civil.fromDays(epochDay)
    val name = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )[month - 1]
    return "$day $name $year"
}
