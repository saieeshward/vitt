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
import ie.shoonya.vitt.sync.SyncStatus
import ie.shoonya.vitt.ui.theme.Vitt
import kotlinx.coroutines.launch

/** What Settings can do about the sheet. Kept as callbacks so the sheet has no idea what Google is. */
class SheetActions(
    val connect: suspend () -> AuthResult,
    val disconnect: suspend () -> Unit,
    val syncNow: suspend () -> Unit,
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
        }
    }

    note?.let {
        Text(it, style = Vitt.type.label, color = Vitt.colors.destructive)
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
