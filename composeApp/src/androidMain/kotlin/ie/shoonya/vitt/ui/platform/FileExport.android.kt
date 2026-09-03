package ie.shoonya.vitt.ui.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The system create-document picker, not a share sheet.
 *
 * `ACTION_CREATE_DOCUMENT` needs no `FileProvider`, no manifest provider entry,
 * no `xml/paths.xml` and no storage permission on any API level: the user picks
 * the destination and the app writes to the `Uri` it is handed. A share sheet
 * would need all four and give the user less say over where the file lands.
 *
 * The picker takes one destination at a time, so a multi-file export is a queue
 * walked one launch per file. The queue is held in state because each pick is a
 * round trip through another app: the user may take a while, and the result
 * arrives on a later frame.
 */
@Composable
actual fun rememberFileExporter(): FileExporter {
    val context = LocalContext.current
    val queue = remember { mutableStateOf<List<ExportFile>>(emptyList()) }

    // Declared before the launcher so the callback can start the next file. The
    // launcher is not available inside its own callback, so the step function is
    // held in state and assigned once the launcher exists.
    val next = remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        // The picker honours the suggested name passed to launch() and adds the
        // extension from the MIME type.
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val remaining = queue.value
        val current = remaining.firstOrNull()
        // Dropped before writing, so a cancelled pick cannot leave the file
        // sitting in the queue to be written to the next destination.
        queue.value = remaining.drop(1)
        if (uri != null && current != null) {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(current.content.encodeToByteArray())
            }
        }
        // Cancelling one file abandons the rest: the user said no to a save
        // dialog, and reopening it for the next file would read as the app
        // refusing to take no for an answer.
        if (uri != null) next.value?.invoke()
    }

    next.value = { queue.value.firstOrNull()?.let { launcher.launch(it.name) } }

    return remember {
        FileExporter { files ->
            if (files.isEmpty()) return@FileExporter
            queue.value = files
            launcher.launch(files.first().name)
        }
    }
}
