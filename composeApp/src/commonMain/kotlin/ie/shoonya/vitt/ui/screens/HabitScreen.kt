package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import ie.shoonya.vitt.ui.CompanionAnimal
import ie.shoonya.vitt.ui.CompanionPet
import ie.shoonya.vitt.ui.theme.Vitt

/**
 * The one place gamification is loud. Everywhere else it is a single ambient dot.
 *
 * The streak counts **days recorded**, never days under budget. Rewarding thrift
 * punishes the month someone flew home for a funeral, and is the fastest route to
 * feeling scolded by software.
 */
@Composable
fun HabitScreen(
    daysRecorded: Int,
    /** The actual days, so the dots can sit on the calendar rather than fill from the left. */
    recordedDays: Set<Int>,
    longestRun: Int,
    today: Int,
    windowDays: Int,
    currencyCount: Int,
    animal: CompanionAnimal?,
    /** Days recorded per currency in the window. Never summed. */
    perCurrency: Map<ie.shoonya.vitt.money.Currency, Int>,
    /** The last few months, oldest first. */
    months: List<ie.shoonya.vitt.model.MonthCoverage>,
    currencyIndex: (ie.shoonya.vitt.money.Currency) -> Int,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Vitt.space.loose)
            .padding(bottom = Vitt.space.section * 2),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Habit", style = Vitt.type.title, color = Vitt.colors.ink)
            androidx.compose.material3.TextButton(onClick = onDone) { Text("Done") }
        }

        // Drawn largest here, because `design-identity.md` calls Habit "the one
        // place gamification is loud" while the daily screens stay quiet. Still,
        // not wandering: the strip is where she walks.
        //
        // Absent entirely when the user picked no pet. The tab still works: the
        // count and the dots are the habit, and the animal was never the data.
        animal?.let {
            CompanionPet(
                daysRecorded = daysRecorded,
                currencyCount = currencyCount,
                animal = it,
                mood = null,
                pixelSize = 5.dp,
            )
        }

        Text(
            "$daysRecorded of the last $windowDays days recorded",
            style = Vitt.type.money,
            color = Vitt.colors.ink,
            textAlign = TextAlign.Center,
        )

        // A rolling count, not a streak that resets to zero. There is no loss
        // event to dread, so a missed day costs nothing to come back from.
        DayDots(recordedDays = recordedDays, today = today, windowDays = windowDays)

        // §5.4: keep "longest" prominent. It is the figure a lapse cannot take
        // away, and stating it beside the rolling count is what makes a gap
        // read as a gap rather than a loss.
        if (longestRun > 1) {
            Text(
                "Longest run $longestRun days",
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
                textAlign = TextAlign.Center,
            )
        }

        // Per currency, because that is how everything else in the app is
        // counted, and because it shows which ledger is the neglected one
        // without saying so.
        if (perCurrency.size > 1) {
            Text("By currency", style = Vitt.type.caption, color = Vitt.colors.inkMuted, modifier = Modifier.fillMaxWidth().padding(top = Vitt.space.snug))
            perCurrency.entries.sortedByDescending { it.value }.forEach { (currency, days) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Vitt.space.hair),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Vitt.colors.currency(currencyIndex(currency))))
                    Text("  ${currency.code}", style = Vitt.type.body, color = Vitt.colors.ink, modifier = Modifier.weight(1f))
                    Text(
                        if (days == 1) "1 day" else "$days days",
                        style = Vitt.type.money,
                        color = Vitt.colors.ink,
                    )
                }
            }
        }

        // Six months of coverage as hairlines, one per month. The current
        // month is measured against the days it has had, so it never reads as
        // the emptiest for being the youngest (§5.2).
        if (months.any { it.recorded > 0 }) {
            Text("Months", style = Vitt.type.caption, color = Vitt.colors.inkMuted, modifier = Modifier.fillMaxWidth().padding(top = Vitt.space.snug))
            months.forEach { m ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Vitt.space.hair),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        ie.shoonya.vitt.time.monthLabel(m.month, ie.shoonya.vitt.time.Civil.fromDays(today).first),
                        style = Vitt.type.label,
                        color = Vitt.colors.inkMuted,
                        modifier = Modifier.width(64.dp),
                    )
                    androidx.compose.foundation.layout.Box(
                        Modifier.weight(1f).height(3.dp).clip(CircleShape)
                            .background(Vitt.colors.inkFaint.copy(alpha = 0.18f)),
                    ) {
                        val share = if (m.elapsed == 0) 0f else m.recorded.toFloat() / m.elapsed
                        if (share > 0f) {
                            androidx.compose.foundation.layout.Box(
                                Modifier.fillMaxWidth(share.coerceIn(0.02f, 1f)).height(3.dp)
                                    .clip(CircleShape).background(Vitt.colors.accent),
                            )
                        }
                    }
                    Text(
                        "  ${m.recorded} of ${m.elapsed}",
                        style = Vitt.type.caption,
                        color = Vitt.colors.inkMuted,
                    )
                }
            }
        }

        Text(
            when {
                daysRecorded == 0 -> "Pip is asleep. Log something and they'll wake up."
                currencyCount > 1 ->
                    "One slot per currency. Coins never move between them."
                else -> "Pip deepens as you keep recording, never with what you spend."
            },
            style = Vitt.type.label,
            color = Vitt.colors.inkMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * One dot per day of the window, oldest on the left and today on the right,
 * filled where something was recorded.
 *
 * On the calendar rather than packed from the left, because a packed row
 * shows a score and this shows a shape: a run, a gap, a return. The shape is
 * the honest thing and it is also the thing that is not a streak counter.
 */
@Composable
private fun DayDots(recordedDays: Set<Int>, today: Int, windowDays: Int) {
    val colors = Vitt.colors
    val span = minOf(windowDays, 30)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        repeat(span) { i ->
            val day = today - (span - 1 - i)
            val filled = day in recordedDays
            Box(
                Modifier.size(if (filled) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(if (filled) colors.accent else colors.inkFaint.copy(alpha = 0.35f)),
            )
        }
    }
}

@Composable
private fun Box(modifier: Modifier) = androidx.compose.foundation.layout.Box(modifier)
