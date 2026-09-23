package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import ie.shoonya.vitt.capture.Category
import ie.shoonya.vitt.model.Transaction
import ie.shoonya.vitt.time.Civil
import ie.shoonya.vitt.ui.VittChip
import ie.shoonya.vitt.ui.theme.Vitt
import kotlinx.coroutines.delay

/**
 * Entries with no category, one at a time, one tap each.
 *
 * Nobody opens forty rows to categorise them one sheet at a time, which is why
 * the "No category" slice only ever grew. Here a tap files the entry and the
 * next one is already showing, so twenty entries are twenty taps and no
 * navigation. A pick teaches the merchant and restates its past entries, so a
 * run of Tesco rows goes in one tap and drops out of the queue together.
 *
 * [queue] is live: whatever the last pick sorted is gone from it on the next
 * composition. Skipping is held here and forgotten on close, so a skipped row
 * comes back next time rather than hiding for good.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SortSheet(
    queue: List<Transaction>,
    /** Most-used spending categories, offered first. */
    frequentSpending: List<Category>,
    frequentIncome: List<Category>,
    onPick: (id: String, Category) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var skipped by remember { mutableStateOf(emptySet<String>()) }
    val left = queue.filterNot { it.id in skipped }
    val current = left.firstOrNull()
    var picked by remember { mutableStateOf(false) }
    // The last pick closes the sheet after a beat long enough to read
    // "Sorted": a Done button at the end of a job already finished is one
    // more tap for nothing. Not when something was skipped, since then the
    // message about it is worth reading.
    if (current == null && picked && skipped.isEmpty()) {
        LaunchedEffect(Unit) {
            delay(700)
            onDone()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (current == null) "Sorted" else "${left.size} to sort",
                style = Vitt.type.title,
                color = Vitt.colors.ink,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            TextButton(onClick = onDone) { Text("Done") }
        }

        if (current == null) {
            Text(
                if (skipped.isEmpty()) "Every entry has a category." else "The ones you skipped will be here next time.",
                style = Vitt.type.body,
                color = Vitt.colors.inkMuted,
            )
            return@Column
        }

        val (_, month, day) = Civil.fromDays(current.day)
        Column(verticalArrangement = Arrangement.spacedBy(Vitt.space.snug)) {
            Text(current.amount.displayUnsigned(), style = Vitt.type.display, color = Vitt.colors.ink)
            Text(
                listOfNotNull(current.merchantLabel ?: current.note ?: "No name", "$day ${MONTHS[month - 1]}").joinToString(" · "),
                style = Vitt.type.body,
                color = Vitt.colors.inkMuted,
            )
        }

        val income = current.amount.isInflow
        val all = Category.entries.filter { it.isPickable && it.isSpending != income }
        val frequent = if (income) frequentIncome else frequentSpending
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight),
            verticalArrangement = Arrangement.spacedBy(Vitt.space.tight),
        ) {
            // The ones this person uses first, then the rest: all of them
            // shown, since here choosing is the whole job.
            (frequent + all).distinct().forEach { category ->
                VittChip(
                    selected = false,
                    onClick = { picked = true; onPick(current.id, category) },
                    label = { Text(category.label, style = Vitt.type.label) },
                )
            }
        }
        TextButton(onClick = { skipped = skipped + current.id }) { Text("Skip") }
    }
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
