package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.ui.VittChip
import ie.shoonya.vitt.ui.theme.Vitt

/**
 * What the activity list is currently showing.
 *
 * A month plus optional narrowing, rather than one exclusive selection: the axes
 * are independent — "September, in rupees, still uncategorised" is a real
 * question and a tab bar cannot ask it.
 */
data class ActivityFilter(
    /** Null means every currency. Never a summed view — only an unfiltered one. */
    val currency: Currency?,
    val needingCategory: Boolean = false,
)

/**
 * The narrowing chips. The period itself lives in [PeriodControl] above.
 *
 * Chips rather than a second tab bar: the design uses [VittChip] throughout, a
 * nested tab row reads as a heavier app than this is, and tabs would force these
 * axes to be exclusive when they are not — "September, in rupees, still
 * uncategorised" is a real question a tab bar cannot ask.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ActivityFilters(
    filter: ActivityFilter,
    currencies: List<Currency>,
    needingCategory: Int,
    onChange: (ActivityFilter) -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.tight),
    ) {
        // Only worth the space once there is more than one currency, which is
        // also the only case where filtering to one means anything.
        if (currencies.size > 1) {
            VittChip(
                selected = filter.currency == null,
                onClick = { onChange(filter.copy(currency = null)) },
                label = { Text("All", style = Vitt.type.label) },
            )
            currencies.forEach { currency ->
                VittChip(
                    selected = filter.currency == currency,
                    onClick = { onChange(filter.copy(currency = currency)) },
                    label = { Text(currency.code, style = Vitt.type.label) },
                )
            }
        }

        // Carries its own count, and hides when there is nothing to review, so
        // it can never be a chip that turns out to match nothing.
        if (needingCategory > 0 || filter.needingCategory) {
            VittChip(
                selected = filter.needingCategory,
                onClick = { onChange(filter.copy(needingCategory = !filter.needingCategory)) },
                label = { Text("$needingCategory need a category", style = Vitt.type.label) },
            )
        }
    }
}

