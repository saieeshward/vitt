package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import ie.shoonya.vitt.time.Civil
import ie.shoonya.vitt.time.Day
import ie.shoonya.vitt.time.Period
import ie.shoonya.vitt.time.Week
import ie.shoonya.vitt.time.Year
import ie.shoonya.vitt.time.YearMonth
import ie.shoonya.vitt.time.periodLabel
import ie.shoonya.vitt.time.periodPhrase
import ie.shoonya.vitt.ui.theme.Vitt

/**
 * One line: what you are looking at, and two arrows.
 *
 * The grain hides behind the label rather than taking a row of its own, which is
 * what keeps this to a single line at every resolution. Tapping the label offers
 * Day, Week, Month, Year and All time — so "All time" is a grain rather than a
 * second control, and the whole thing is *fewer* pieces of chrome than a month
 * stepper plus an All-time button.
 *
 * Deliberately not a date-range picker. Two calendars is the most flexible and
 * least simple answer, it is a modal for something done constantly, and an
 * arbitrary range is usually a stand-in for "that trip" — which is a label, not
 * a pair of dates, and survives the trip crossing a month boundary.
 */
@Composable
fun PeriodControl(
    period: Period?,
    /** The span of days with anything in them, so stepping stops at the edges. */
    dataRange: IntRange?,
    today: Int,
    onChange: (Period?) -> Unit,
    /** Opens the grain picker. A sheet, like every other picker in this app. */
    onPickGrain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Stepping is bounded by the data, not by the calendar: walking back
            // through empty years is a worse dead end than a disabled arrow.
            val older = period?.previous()?.takeIf { dataRange != null && it.lastDay >= dataRange.first }
            val newer = period?.next()?.takeIf { dataRange != null && it.firstDay <= dataRange.last }

            if (period != null) {
                TextButton(
                    onClick = { older?.let(onChange) },
                    enabled = older != null,
                    // A screen reader gets nothing usable from "\u2039".
                    modifier = Modifier.semantics { contentDescription = "Earlier" },
                ) {
                    // Decorative: the button is named above, and without this the
                    // glyph is announced after the name.
                    Text("\u2039", style = Vitt.type.title, modifier = Modifier.clearAndSetSemantics {})
                }
            }

            Row(
                modifier = Modifier
                    .clickable(onClick = onPickGrain, onClickLabel = "Change period")
                    .padding(horizontal = Vitt.space.tight, vertical = Vitt.space.hair)
                    // Merges the label and the chevron into one node, so it does
                    // not read as the period followed by a stray "\u2304".
                    .semantics(mergeDescendants = true) { role = Role.Button },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    periodLabel(period, today),
                    style = Vitt.type.title,
                    color = Vitt.colors.ink,
                )
                // A small mark, so the label reads as something you can press.
                Text(
                    "  ⌄",
                    style = Vitt.type.label,
                    color = Vitt.colors.inkMuted,
                    // Else the period reads as "September, ⌄".
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }

            if (period != null) {
                TextButton(
                    onClick = { newer?.let(onChange) },
                    enabled = newer != null,
                    modifier = Modifier.semantics { contentDescription = "Later" },
                ) {
                    Text("\u203a", style = Vitt.type.title, modifier = Modifier.clearAndSetSemantics {})
                }
            }
        }

    }
}

/**
 * Choosing a grain: Day, Week, Month, Year, All time.
 *
 * A sheet rather than a dropdown. Every other picker in this app is a sheet, and
 * a `DropdownMenu` anchored inside a `LazyColumn` item is anchoring its position
 * to something the list is free to dispose — it silently failed to open here.
 *
 * Each option lands on the period containing today, so changing grain never
 * strands the user in an empty stretch.
 */
@androidx.compose.runtime.Composable
fun GrainSheet(
    period: Period?,
    today: Int,
    onPick: (Period?) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.snug),
    ) {
        Text("Show", style = Vitt.type.title, color = Vitt.colors.ink)

        listOf<Pair<String, Period?>>(
            "Day" to Day(today),
            "Week" to Week.containing(today),
            "Month" to YearMonth.of(today),
            "Year" to Year(Civil.fromDays(today).first),
            "All time" to null,
        ).forEach { (name, candidate) ->
            val selected = when {
                candidate == null -> period == null
                else -> period?.grain == candidate.grain
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(candidate) })
                    .padding(vertical = Vitt.space.base),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    name,
                    style = Vitt.type.body,
                    color = if (selected) Vitt.colors.accent else Vitt.colors.ink,
                )
                // The period it would land on, so the choice is concrete rather
                // than abstract — omitted for All time, where it would just
                // repeat the name back.
                val lands = periodLabel(candidate, today)
                if (lands != name) {
                    Text(lands, style = Vitt.type.label, color = Vitt.colors.inkMuted)
                }
            }
        }
    }
}
