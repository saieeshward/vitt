package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import ie.shoonya.vitt.capture.Category
import ie.shoonya.vitt.capture.CategorySource
import ie.shoonya.vitt.capture.MerchantName
import ie.shoonya.vitt.model.Transaction
import ie.shoonya.vitt.ui.theme.Vitt

/**
 * Correcting a category, which is how the app learns.
 *
 * This closes §6's loop: tier 3 is *ask the user, then write a tier-1 rule*, so
 * without a way to correct one there is no tier 1 at all and the shipped keyword
 * list is the ceiling. Picking a category here teaches the merchant.
 *
 * It also shows which tier produced the current answer, because a category the
 * user disagrees with is only debuggable if the app says where it came from.
 */
@Composable
fun CategorySheet(
    transaction: Transaction,
    /** Past entries from the same merchant that a rule could restate. */
    pastCount: Int,
    onPick: (Category, teach: Boolean, applyToPast: Boolean) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val merchantKey = transaction.merchant?.let { MerchantName.key(it) }
    // On by default, because the whole value of tier 1 is not being asked twice.
    // Off is for the genuine one-off — a restaurant charge at a supermarket
    // should not retrain the supermarket.
    var remember by remember { mutableStateOf(true) }
    // Default on when there is anything to fix: a rule that leaves visibly wrong
    // rows on the same screen reads as broken. Entries the user categorised by
    // hand are excluded upstream and are never touched.
    var applyToPast by remember { mutableStateOf(true) }

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
            Text(
                transaction.merchantLabel ?: "Category",
                style = Vitt.type.title,
                color = Vitt.colors.ink,
                maxLines = 1,
            )
            TextButton(onClick = onDone) { Text("Done") }
        }

        Text(
            transaction.amount.display(),
            style = Vitt.type.money,
            color = Vitt.colors.ink,
        )

        // Provenance. Stated plainly rather than hidden in a debug screen: when
        // the answer is wrong, the user is the one who can tell, and knowing
        // whether a rule or a shipped guess produced it explains what to do.
        transaction.categorySource?.let { source ->
            Text(
                when (source) {
                    CategorySource.LEARNED -> "From a rule you taught."
                    CategorySource.SEED -> "Guessed from the merchant name."
                    CategorySource.MANUAL -> "You chose this."
                },
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )
        } ?: Text(
            "Not categorised yet.",
            style = Vitt.type.label,
            color = Vitt.colors.inkMuted,
        )

        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight),
            verticalArrangement = Arrangement.spacedBy(Vitt.space.tight),
        ) {
            // Income and Transfer are offered too. An import can legitimately be
            // either, and hiding them would leave those rows uncorrectable.
            Category.entries.forEach { category ->
                FilterChip(
                    selected = transaction.categoryOrNull == category,
                    onClick = {
                        onPick(
                            category,
                            remember && merchantKey != null,
                            applyToPast && pastCount > 0,
                        )
                    },
                    label = { Text(category.label, style = Vitt.type.label) },
                )
            }
        }

        if (merchantKey != null) {
            FilterChip(
                selected = remember,
                onClick = { remember = !remember },
                label = { Text("Remember for “$merchantKey”", style = Vitt.type.label) },
            )
            Text(
                if (remember) {
                    "Every future entry from this merchant will use what you pick, " +
                        "so it only needs doing once."
                } else {
                    "This entry only. The merchant keeps whatever it had."
                },
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )

            if (pastCount > 0) {
                FilterChip(
                    selected = applyToPast,
                    onClick = { applyToPast = !applyToPast },
                    label = {
                        Text(
                            if (pastCount == 1) "Fix 1 earlier entry"
                            else "Fix $pastCount earlier entries",
                            style = Vitt.type.label,
                        )
                    },
                )
                Text(
                    "Anything you categorised by hand is left alone.",
                    style = Vitt.type.label,
                    color = Vitt.colors.inkFaint,
                )
            }
        } else {
            Text(
                "There is no merchant on this entry, so there is nothing to " +
                    "remember it against — the category applies to this one only.",
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )
        }
    }
}
