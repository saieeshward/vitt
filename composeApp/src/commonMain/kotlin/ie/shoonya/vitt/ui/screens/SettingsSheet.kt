package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import ie.shoonya.vitt.sync.SyncStatus
import ie.shoonya.vitt.ui.CompanionAnimal
import ie.shoonya.vitt.ui.theme.AccentChoice
import ie.shoonya.vitt.ui.theme.ThemeChoice
import ie.shoonya.vitt.config.Links
import ie.shoonya.vitt.ui.platform.rememberLinkOpener
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
    companion: CompanionAnimal?,
    onCompanionChange: (CompanionAnimal?) -> Unit,
    theme: ThemeChoice,
    onThemeChange: (ThemeChoice) -> Unit,
    accent: AccentChoice,
    onAccentChange: (AccentChoice) -> Unit,
    currencyCount: Int,
    swipeCards: Boolean,
    onSwipeCardsChange: (Boolean) -> Unit,
    syncStatus: SyncStatus,
    sheetActions: SheetActions,
    now: () -> Long,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Vitt.space.loose)
            // Clear of the home indicator, so the last control is not the one
            // the thumb is resting over.
            .padding(bottom = Vitt.space.section * 2),
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

        // Appearance first. It is the only thing here most people will ever
        // change, and the habit switch is a decision made once.
        AppearanceSection(
            companion = companion,
            onCompanionChange = onCompanionChange,
            theme = theme,
            onThemeChange = onThemeChange,
            accent = accent,
            onAccentChange = onAccentChange,
            currencyCount = currencyCount,
        )

        Text("Home", style = Vitt.type.caption, color = Vitt.colors.inkMuted)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = swipeCards, role = Role.Switch, onValueChange = onSwipeCardsChange)
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Swipe between currency cards",
                style = Vitt.type.body,
                color = Vitt.colors.ink,
                modifier = Modifier.fillMaxWidth(0.7f),
            )
            Switch(checked = swipeCards, onCheckedChange = null)
        }
        Text(
            if (swipeCards) "One card at a time, with the next peeking in. Accounts stay in reach."
            else "Every card stacked. Longer with several currencies.",
            style = Vitt.type.label,
            color = Vitt.colors.inkMuted,
        )

        Text("Habit", style = Vitt.type.caption, color = Vitt.colors.inkMuted)

        Row(
            // The whole row toggles, which is both how every settings list on
            // the platform behaves and the only way this control has a name:
            // a bare `Switch` beside a sibling `Text` announces itself as an
            // unnamed switch with no state, and the off switch is the one
            // setting PLAN.md §5 insists must be easy to find.
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = gamificationEnabled,
                    role = Role.Switch,
                    onValueChange = onGamificationChange,
                )
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Count the days I record",
                style = Vitt.type.body,
                color = Vitt.colors.ink,
                modifier = Modifier.fillMaxWidth(0.7f),
            )
            // The row owns the gesture and the state now, so the switch is the
            // picture of it. `null` is what stops it taking a second focus stop.
            Switch(checked = gamificationEnabled, onCheckedChange = null)
        }

        // Says what it does and what it does not, in the tone §5.6 sets: no
        // disappointment, no "should", and the absence stated plainly rather
        // than left as a gap the user has to infer.
        Text(
            if (gamificationEnabled) {
                "Counts days you recorded on, never how much you spent."
            } else {
                "Off. The Habit tab is hidden. Pip stays, but no longer changes."
            },
            style = Vitt.type.label,
            color = Vitt.colors.inkMuted,
        )

        Text(
            "Your transactions, budgets and sheet are untouched either way.",
            style = Vitt.type.label,
            color = Vitt.colors.inkFaint,
        )

        // Last, because it is set up once and then forgotten about. The status
        // line is the one part of it people come back for.
        SheetSection(status = syncStatus, actions = sheetActions, now = now)

        // Where the data is, said once, plainly. Both stores want the privacy
        // policy reachable from inside the app; the link appears the moment
        // `Links.PRIVACY_POLICY` is filled in and not before.
        Text("About", style = Vitt.type.caption, color = Vitt.colors.inkMuted)
        Text(
            "No account and no server. Your entries live on this phone and, if you connect Google, in a spreadsheet in your own Drive.",
            style = Vitt.type.label,
            color = Vitt.colors.inkMuted,
        )
        val open = rememberLinkOpener()
        Row(horizontalArrangement = Arrangement.spacedBy(Vitt.space.snug)) {
            if (Links.PRIVACY_POLICY.isNotBlank()) {
                TextButton(onClick = { open(Links.PRIVACY_POLICY) }) { Text("Privacy policy") }
            }
            if (Links.SUPPORT.isNotBlank()) {
                TextButton(onClick = { open(Links.SUPPORT) }) { Text("Report a problem") }
            }
        }
    }
}
