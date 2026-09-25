package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    /** Opens Add, from the empty state. */
    onAdd: (() -> Unit)? = null,
    /** Opens the one-tap-each pass over entries with no category. */
    onSort: (() -> Unit)? = null,
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
    ) {
        item { Text("Activity", style = Vitt.type.display, color = Vitt.colors.ink, modifier = Modifier.padding(bottom = Vitt.space.hair)) }

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

        // Filtered to what needs a category, the list is a to-do; this does it
        // a tap an entry instead of a sheet an entry.
        if (filter.needingCategory && days.isNotEmpty() && onSort != null) {
            item {
                ie.shoonya.vitt.ui.VittChip(
                    selected = false,
                    onClick = onSort,
                    label = { Text("Sort them, one tap each", style = Vitt.type.label) },
                    modifier = Modifier.padding(top = Vitt.space.tight),
                )
            }
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
                // An empty list with no way forward is a dead end. Only when no
                // filter emptied it: otherwise the way out is the filter itself.
                if (onAdd != null && !filter.needingCategory && filter.currency == null) {
                    androidx.compose.material3.TextButton(
                        onClick = onAdd,
                        contentPadding = PaddingValues(0.dp),
                    ) { Text("Log something", style = Vitt.type.body) }
                }
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

        // A passbook: the date printed once in the margin, then one ruled line
        // per record under it. No headings between days, because the margin
        // already says where one day ends and the next begins.
        days.forEach { (day, transactions) ->
            items(transactions.size) { i ->
                TransactionRow(
                    transactions[i],
                    dateLabel = if (i == 0) marginDate(day, today) else null,
                    currencyIndex = currencyIndex,
                    onEdit = onEdit,
                )
            }
        }
    }
}

/** The ruled line every record sits on. Fixed, so the page keeps its rhythm. */
private val LINE_HEIGHT = 48.dp

/** The margin column holding the date, and its double rule. */
private val MARGIN_WIDTH = 60.dp

@Composable
private fun TransactionRow(
    txn: Transaction,
    /** The date, printed only on the first line of its day. */
    dateLabel: String?,
    currencyIndex: (Currency) -> Int,
    onEdit: (Transaction) -> Unit,
) {
    val colors = Vitt.colors
    val rule = colors.hairline
    val margin = colors.accentSoft.copy(alpha = 0.45f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LINE_HEIGHT)
            .clickable { onEdit(txn) }
            // The faint rule under every line and the double rule down the
            // margin are drawn, not laid out, so they cost nothing and never
            // shift a figure.
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(rule, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                val x = MARGIN_WIDTH.toPx()
                drawLine(margin, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                drawLine(margin, Offset(x + 3.dp.toPx(), 0f), Offset(x + 3.dp.toPx(), size.height), strokeWidth = 1.dp.toPx())
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            dateLabel.orEmpty(),
            style = Vitt.type.mono,
            color = colors.inkMuted,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(MARGIN_WIDTH).padding(end = Vitt.space.snug),
        )
        // What the person wrote beats what the app guessed: a note names the
        // entry better than its category does.
        val name = txn.merchantLabel ?: txn.note ?: txn.categoryOrNull?.label
        // True when the category is doing double duty as the title. An entry
        // typed on the keypad and left unnamed has nothing else to show, and
        // printing the category again beside it read "Groceries Groceries".
        val titleIsCategory = txn.merchantLabel == null && txn.note == null
        val detail = buildList {
            // The label, not the stored code — "groceries" reads as a
            // database field.
            if (!titleIsCategory) {
                txn.categoryOrNull?.let { add(it.label) } ?: txn.category?.let { add(it) }
            }
            // Provenance only where it is worth a word: the one tier a user
            // can fix is the one they taught.
            if (txn.categorySource == CategorySource.LEARNED) add("you taught me")
            // The note rides along when a merchant already holds the title.
            if (txn.merchantLabel != null) txn.note?.let { add(it) }
            if (txn.isSplit) add("your share")
            // The way out of an uncategorised row, on the row itself.
            if (txn.categoryOrNull == null && txn.category == null) add("add a category")
        }.joinToString(" · ")
        // The name on the line, the small print under it. Stacked rather than
        // run on, because a name cut to "Dinner with A..." to make room for
        // its category has the priorities backwards.
        Column(
            modifier = Modifier.weight(1f).padding(start = Vitt.space.base, end = Vitt.space.snug),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                // Never a bare dash. Unnamed says what it is, not what it
                // lacks: "No description" on a first entry read like a mistake.
                name ?: if (txn.amount.isInflow) "Income" else "Expense",
                style = Vitt.type.body,
                color = if (name == null) colors.inkMuted else colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotEmpty()) {
                Text(detail, style = Vitt.type.caption, color = colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        ie.shoonya.vitt.ui.CurrencyMark(currencyIndex(txn.amount.currency), size = 6.dp)
        Text(
            txn.amount.display(),
            style = Vitt.type.money,
            // Spending is plain text; only income takes colour. Colouring an
            // expense would be the app passing judgement on it.
            color = if (txn.amount.isInflow) colors.accentSoft else colors.ink,
            modifier = Modifier.padding(start = Vitt.space.snug),
        )
    }
}

/**
 * The date as the margin prints it: today and yesterday by name, the rest as
 * day and month in the ledger's small capitals.
 */
internal fun marginDate(epochDay: Int, today: Int): String = when (epochDay) {
    today -> "TODAY"
    today - 1 -> "YEST"
    else -> shortDate(epochDay)
}

/** "24 SEP": the date in the ledger's small capitals. */
internal fun shortDate(epochDay: Int): String {
    val (_, m, d) = ie.shoonya.vitt.time.Civil.fromDays(epochDay)
    val month = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")[m - 1]
    return "$d $month"
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
