package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import ie.shoonya.vitt.time.Period
import ie.shoonya.vitt.time.periodPhrase
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
    filter: ActivityFilter,
    period: Period?,
    dataRange: IntRange?,
    today: Int,
    currencies: List<Currency>,
    needingCategory: Int,
    onFilterChange: (ActivityFilter) -> Unit,
    onPeriodChange: (Period?) -> Unit,
    onPickGrain: () -> Unit,
    /** Room kept at the foot for the companion's band, so the last rows never sit under her. */
    companionInset: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Vitt.space.loose,
            top = Vitt.space.loose,
            end = Vitt.space.loose,
            bottom = Vitt.space.loose + companionInset,
        ),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.hair),
    ) {
        item { Text("Activity", style = Vitt.type.display, color = Vitt.colors.ink) }

        item {
            PeriodControl(
                period = period,
                dataRange = dataRange,
                today = today,
                onChange = onPeriodChange,
                onPickGrain = onPickGrain,
            )
        }

        item {
            ActivityFilters(
                filter = filter,
                currencies = currencies,
                needingCategory = needingCategory,
                onChange = onFilterChange,
            )
        }

        if (days.isEmpty()) {
            item {
                // Says which filter emptied the list, so the way out is obvious.
                // "Nothing here yet" is a lie when there are two hundred entries
                // one chip away.
                Text(
                    when {
                        filter.needingCategory ->
                            "Everything here has a category. Nothing to review."
                        filter.currency != null && period != null ->
                            "Nothing in ${filter.currency.code} " +
                                periodPhrase(period, today) + "."
                        filter.currency != null -> "Nothing in ${filter.currency.code} yet."
                        period != null ->
                            "Nothing recorded " + periodPhrase(period, today) + "."
                        else -> "Nothing here yet."
                    },
                    style = Vitt.type.body,
                    color = Vitt.colors.inkMuted,
                    modifier = Modifier.padding(top = Vitt.space.tight),
                )
            }
        } else if (filter.currency != null) {
            // A single-currency view can carry a total, because there is only one
            // currency in it. The unfiltered view deliberately cannot — that is
            // the figure §0.6 forbids, and its absence is the point.
            item {
                val total = days.flatMap { it.second }
                    .filter { it.isSpend }
                    .fold(ie.shoonya.vitt.money.Money(0, filter.currency)) { acc, t ->
                        acc + t.amount.abs()
                    }
                Text(
                    "${total.displayUnsigned()} out ${periodPhrase(period, today)}",
                    style = Vitt.type.label,
                    color = Vitt.colors.inkMuted,
                    modifier = Modifier.padding(bottom = Vitt.space.tight),
                )
            }
        }

        days.forEach { (day, transactions) ->
            item {
                Text(
                    relativeDay(day, today),
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
        // What the person wrote beats what the app guessed: a note names the
        // entry better than its category does.
        val name = txn.merchantLabel ?: txn.note ?: txn.categoryOrNull?.label
        Monogram(
            name = name,
            hue = colors.currency(currencyIndex(txn.amount.currency)),
        )
        Column(
            modifier = Modifier.padding(start = Vitt.space.snug).fillMaxWidth(0.62f),
        ) {
            Text(
                // Never a bare dash. A row the user typed an amount into and
                // nothing else still has to say what it is, and "—" says less
                // than nothing: it reads as a rendering fault.
                name ?: "No description",
                style = Vitt.type.body,
                color = if (name == null) colors.inkMuted else colors.ink,
                maxLines = 1,
            )
            val subtitle = buildList {
                // The label, not the stored code — "groceries" reads as a
                // database field.
                txn.categoryOrNull?.let { add(it.label) } ?: txn.category?.let { add(it) }
                // Provenance, but only where it is worth a word. §6 wants this
                // as a debugging affordance and it stays one: a wrong category
                // with nothing here came from the shipped keyword list by
                // elimination. Printing "seed" on nine rows in ten was jargon
                // on almost every line of the busiest screen in the app, and
                // the one tier a user can actually fix is the one they taught.
                if (txn.categorySource == CategorySource.LEARNED) add("you taught me")
                // The note rides along when a merchant already holds the title.
                if (txn.merchantLabel != null) txn.note?.let { add(it) }
                if (txn.isSplit) add("your share")
                // The way out of an uncategorised row, on the row itself.
                if (txn.categoryOrNull == null && txn.category == null) add("add a category")
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

/**
 * A merchant's initials in a ring.
 *
 * Taken from how Revolut's transaction list reads: the leading circle is what
 * makes a long list scannable, because a shape and a letter land before any of
 * the text does. Revolut fills the circle with a brand logo; this app cannot —
 * fetching logos would mean an image CDN, a network call per row, and telling a
 * third party what its user buys, which is the whole thing VITT is not.
 *
 * So it is initials, and the ring rather than a filled disc carries the
 * currency: `design-identity.md` puts chroma in lines and dots and never in a
 * filled shape, and a list of filled colour discs would read as a chart.
 */
@Composable
private fun Monogram(name: String?, hue: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .border(1.5.dp, hue, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initialsOf(name),
            style = Vitt.type.caption,
            color = Vitt.colors.inkMuted,
            maxLines = 1,
        )
    }
}

/**
 * One or two letters, from the words a person would say.
 *
 * Two initials for "Coffee Angel" and one for "Tesco", which is what keeps the
 * ring from looking half-empty on multi-word names without crowding short ones.
 */
internal fun initialsOf(name: String?): String {
    val words = name?.split(' ', '-')?.filter { it.isNotBlank() }.orEmpty()
    return when (words.size) {
        0 -> ""
        1 -> words[0].take(1).uppercase()
        else -> words[0].take(1).uppercase() + words[1].take(1).uppercase()
    }
}

/**
 * A day header, named relative to today for the two days that have names.
 *
 * Every banking app does this because it is how people hold recent dates: "3
 * September 2026" makes the reader work out whether that was yesterday, and the
 * top of this list is almost always yesterday or today.
 */
internal fun relativeDay(epochDay: Int, today: Int): String = when (epochDay) {
    today -> "Today"
    today - 1 -> "Yesterday"
    // The year only earns its place once the list has left this year behind.
    // "3 September 2026" reading under "Today" is three words to say one.
    else -> {
        val full = formatDay(epochDay)
        val year = Civil.fromDays(epochDay).first
        if (year == Civil.fromDays(today).first) full.removeSuffix(" $year") else full
    }
}
