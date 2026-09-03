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
    windowDays: Int,
    currencyCount: Int,
    animal: CompanionAnimal?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Vitt.space.loose),
        verticalArrangement = Arrangement.spacedBy(Vitt.space.base),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Habit", style = Vitt.type.title, color = Vitt.colors.ink, modifier = Modifier.fillMaxWidth())

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
        DayDots(daysRecorded = daysRecorded, windowDays = windowDays)

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

@Composable
private fun DayDots(daysRecorded: Int, windowDays: Int) {
    val colors = Vitt.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        repeat(minOf(windowDays, 30)) { i ->
            val filled = i < daysRecorded
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
