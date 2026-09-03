package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.time.YearMonth
import ie.shoonya.vitt.ui.theme.Vitt

/**
 * What the activity list is currently showing.
 *
 * A month plus optional narrowing, rather than one exclusive selection: the axes
 * are independent — "September, in rupees, still uncategorised" is a real
 * question and a tab bar cannot ask it.
 */
data class ActivityFilter(
    /** Null means every month. */
    val month: YearMonth?,
    /** Null means every currency. Never a summed view — only an unfiltered one. */
    val currency: Currency?,
    val needingCategory: Boolean = false,
)

/**
 * The filter row: a month stepper, then chips.
 *
 * Chips rather than a second tab bar. The design already uses `FilterChip`
 * throughout, a nested tab row reads as a heavier app than this is, and tabs
 * would force the axes to be mutually exclusive when they are not.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ActivityFilters(
    filter: ActivityFilter,
    /** Months with anything in them, newest first — the stepper's bounds. */
    months: List<YearMonth>,
    currencies: List<Currency>,
    needingCategory: Int,
    onChange: (ActivityFilter) -> Unit,
    monthName: (YearMonth) -> String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Vitt.space.snug)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (filter.month == null) {
                Text("All time", style = Vitt.type.title, color = Vitt.colors.ink)
                TextButton(
                    onClick = { onChange(filter.copy(month = months.firstOrNull())) },
                    enabled = months.isNotEmpty(),
                ) { Text("This month") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Stepping stops at the ends of the data rather than walking
                    // into empty years.
                    val older = months.filter { it < filter.month }.maxOrNull()
                    val newer = months.filter { it > filter.month }.minOrNull()
                    TextButton(
                        onClick = { older?.let { onChange(filter.copy(month = it)) } },
                        enabled = older != null,
                    ) { Text("‹") }
                    Text(
                        monthName(filter.month),
                        style = Vitt.type.title,
                        color = Vitt.colors.ink,
                    )
                    TextButton(
                        onClick = { newer?.let { onChange(filter.copy(month = it)) } },
                        enabled = newer != null,
                    ) { Text("›") }
                }
                TextButton(onClick = { onChange(filter.copy(month = null)) }) {
                    Text("All time")
                }
            }
        }

        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight),
            verticalArrangement = Arrangement.spacedBy(Vitt.space.tight),
        ) {
            // Only worth the space once there is more than one currency, which
            // is also the only case where filtering by one means anything.
            if (currencies.size > 1) {
                FilterChip(
                    selected = filter.currency == null,
                    onClick = { onChange(filter.copy(currency = null)) },
                    label = { Text("All", style = Vitt.type.label) },
                )
                currencies.forEach { currency ->
                    FilterChip(
                        selected = filter.currency == currency,
                        onClick = { onChange(filter.copy(currency = currency)) },
                        label = { Text(currency.code, style = Vitt.type.label) },
                    )
                }
            }

            // Surfaced with a count, because an empty chip is a dead end and a
            // chip that turns out to match nothing is worse than no chip.
            if (needingCategory > 0 || filter.needingCategory) {
                FilterChip(
                    selected = filter.needingCategory,
                    onClick = { onChange(filter.copy(needingCategory = !filter.needingCategory)) },
                    label = {
                        Text("$needingCategory need a category", style = Vitt.type.label)
                    },
                )
            }
        }
    }
}

/**
 * A month, named for a person.
 *
 * The year is dropped for the current year: "September" is how someone refers to
 * a month they are standing in, and "September 2026" reads like a database
 * export.
 */
fun monthLabel(month: YearMonth, currentYear: Int): String {
    val name = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )[month.month - 1]
    return if (month.year == currentYear) name else "$name ${month.year}"
}
