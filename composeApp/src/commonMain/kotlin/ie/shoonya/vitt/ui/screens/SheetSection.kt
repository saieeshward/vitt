package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import ie.shoonya.vitt.auth.AuthResult
import ie.shoonya.vitt.sheets.SheetsError
import ie.shoonya.vitt.sheets.DerivedTabs
import ie.shoonya.vitt.sheets.SheetDrift
import ie.shoonya.vitt.sync.SyncStatus
import ie.shoonya.vitt.ui.theme.Vitt
import kotlinx.coroutines.launch

/** What Settings can do about the sheet. Kept as callbacks so the sheet has no idea what Google is. */
class SheetActions(
    val connect: suspend () -> AuthResult,
    val disconnect: suspend () -> Unit,
    val syncNow: suspend () -> Unit,
    /** The user answering drift with "keep the app's version". Never automatic. */
    val overwriteSheet: suspend () -> Unit = {},
)

/**
 * The Google Sheets row in Settings.
 *
 * Connecting is opt-in, from here and nowhere else. The app is complete
 * without it, which is what lets a reviewer use every screen with no account
 * and what keeps §0's "no app-side account" honest: the only sign-in there is
 * belongs to Google, and it is for the user's own file.
 */
@Composable
fun SheetSection(
    status: SyncStatus,
    actions: SheetActions,
    /** Wall-clock now, for "a moment ago" wording. */
    now: () -> Long,
    /** What the last rewrite of the readable tab did, or null before the first. */
    derived: DerivedTabs.Outcome? = null,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    Text("Google Sheet", style = Vitt.type.caption, color = Vitt.colors.inkMuted)

    when (status) {
        SyncStatus.Off -> {
            Text(
                "Keep a copy of every entry in a spreadsheet in your own Google Drive.",
                style = Vitt.type.body,
                color = Vitt.colors.ink,
            )
            Text(
                "VITT can only see the one file it creates. Nothing else in your Drive.",
                style = Vitt.type.label,
                color = Vitt.colors.inkMuted,
            )
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    note = null
                    scope.launch {
                        note = when (val r = actions.connect()) {
                            is AuthResult.Code -> null
                            is AuthResult.Cancelled -> null
                            is AuthResult.Failed -> "Could not connect. ${r.reason}"
                        }
                        busy = false
                    }
                },
            ) { Text(if (busy) "Opening Google" else "Connect Google Drive") }
        }

        else -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    status.describe(now()),
                    style = Vitt.type.body,
                    color = if (status is SyncStatus.Failed) Vitt.colors.destructive else Vitt.colors.ink,
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                TextButton(
                    enabled = status !is SyncStatus.Syncing && !busy,
                    onClick = { scope.launch { actions.syncNow() } },
                ) { Text("Sync now") }
            }
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch { actions.disconnect(); busy = false }
                },
            ) { Text("Disconnect", color = Vitt.colors.destructive) }
            Text(
                "Disconnecting keeps everything on this phone and leaves the spreadsheet in your Drive.",
                style = Vitt.type.label,
                color = Vitt.colors.inkFaint,
            )

            // Only when there is something to say. A line reporting that a tab
            // was rewritten successfully is a line about plumbing, and this
            // screen does not report plumbing.
            (derived as? DerivedTabs.Outcome.Held)?.let { held ->
                Text(
                    held.verdict.sentence(),
                    style = Vitt.type.body,
                    color = Vitt.colors.ink,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(
                    // Said plainly, because the choice is not obvious and the
                    // app is not going to make it. Both sides are kept until
                    // somebody decides.
                    "Your spreadsheet and this phone disagree, so VITT has left " +
                        "the Transactions tab alone. Nothing is lost either way.",
                    style = Vitt.type.label,
                    color = Vitt.colors.inkMuted,
                )
                if (held.verdict is SheetDrift.Verdict.Drifted) {
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch { actions.overwriteSheet(); busy = false }
                        },
                    ) { Text("Replace the tab with this phone's version") }
                }
            }
        }
    }

    note?.let {
        Text(it, style = Vitt.type.label, color = Vitt.colors.destructive)
    }
}

/**
 * What the held refresh found, in one sentence somebody can act on.
 *
 * Named and counted, because "the sheet has changed" tells a person nothing
 * about whether it matters. One change is worth quoting; several are worth
 * counting, since a list of forty would be a screen of its own and this is a
 * row in Settings.
 */
private fun SheetDrift.Verdict.sentence(): String = when (this) {
    is SheetDrift.Verdict.Clean -> ""
    is SheetDrift.Verdict.Unusable -> "The Transactions tab is not the shape VITT wrote: $why."
    is SheetDrift.Verdict.Drifted -> {
        val first = changes.first()
        if (changes.size == 1) {
            when (first) {
                is SheetDrift.Change.Edited ->
                    "${first.column} was changed to \"${first.now}\" in your spreadsheet."
                is SheetDrift.Change.Added -> "A row was added to your spreadsheet."
                is SheetDrift.Change.Removed -> "A row was deleted from your spreadsheet."
            }
        } else {
            "${changes.size} things were changed in your spreadsheet."
        }
    }
}

/** One line, in the tone of the rest of Settings: what is true, not what went wrong inside. */
private fun SyncStatus.describe(now: Long): String = when (this) {
    SyncStatus.Off -> "Not connected"
    SyncStatus.Syncing -> "Syncing"
    is SyncStatus.Idle -> buildString {
        val at = lastSyncedAt
        append(
            when {
                pending > 0 -> "$pending waiting to sync"
                at == null -> "Connected"
                else -> "Up to date, ${ago(now - at)}"
            }
        )
        if (poisoned > 0) append(". $poisoned the sheet refused")
    }
    is SyncStatus.Failed -> when (error) {
        is SheetsError.Unauthorized -> "Signed out by Google. Connect again"
        is SheetsError.FileNotAccessible -> "Cannot reach the spreadsheet"
        is SheetsError.RateLimited -> "Google asked us to wait. Will retry"
        is SheetsError.Transport -> "Offline. $pending waiting"
        else -> "Sync paused. $pending waiting"
    }
}

private fun ago(millis: Long): String = when {
    millis < 60_000 -> "just now"
    millis < 3_600_000 -> "${millis / 60_000} min ago"
    millis < 86_400_000 -> "${millis / 3_600_000} h ago"
    else -> "${millis / 86_400_000} d ago"
}
