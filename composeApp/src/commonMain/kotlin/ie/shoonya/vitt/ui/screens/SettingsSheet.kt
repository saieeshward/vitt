package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ie.shoonya.vitt.ui.theme.Vitt

/**
 * Settings, reached from the Ledgers header rather than a tab.
 *
 * The design is explicit that monthly visits must not slow the daily path, so
 * this lives behind an icon: capture is the thing that happens every day and owns
 * the shortest route.
 */
@Composable
fun SettingsSheet(
    gamificationEnabled: Boolean,
    onGamificationChange: (Boolean) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            Text("Settings", style = Vitt.type.title, color = Vitt.colors.ink)
            TextButton(onClick = onDone) { Text("Done") }
        }

        Text("Habit", style = Vitt.type.caption, color = Vitt.colors.inkMuted)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Count the days I record",
                style = Vitt.type.body,
                color = Vitt.colors.ink,
                modifier = Modifier.fillMaxWidth(0.7f),
            )
            Switch(checked = gamificationEnabled, onCheckedChange = onGamificationChange)
        }

        // Says what it does and what it does not, in the tone §5.6 sets: no
        // disappointment, no "should", and the absence stated plainly rather
        // than left as a gap the user has to infer.
        Text(
            if (gamificationEnabled) {
                "The Habit tab shows how many of the last 30 days you recorded " +
                    "something on. It counts recording, never how much you spent — " +
                    "there is nothing here that can be lost, and nothing that " +
                    "rewards spending more."
            } else {
                "Off. The Habit tab is hidden and nothing counts your days. Pip " +
                    "stays, because the coin slots are how the app explains that " +
                    "currencies are never converted — but Pip no longer changes " +
                    "with anything you do."
            },
            style = Vitt.type.label,
            color = Vitt.colors.inkMuted,
        )

        Text(
            "Turning this off changes nothing about your transactions, budgets or " +
                "sheet.",
            style = Vitt.type.label,
            color = Vitt.colors.inkFaint,
        )
    }
}
