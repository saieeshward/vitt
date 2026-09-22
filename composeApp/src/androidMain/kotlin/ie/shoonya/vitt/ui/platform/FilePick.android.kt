package ie.shoonya.vitt.ui.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The system open-document picker, the counterpart to the create-document one
 * the exporter uses.
 *
 * `ACTION_OPEN_DOCUMENT` needs no storage permission on any API level: the user
 * picks the file and the app is handed a `Uri` it may read once. Asking for
 * READ_EXTERNAL_STORAGE to do the same job would be a sensitive permission for
 * no gain, and Play asks awkward questions about those.
 *
 * Two MIME types, because `text/csv` alone hides the file a user is trying to
 * pick more often than not: plenty of banks hand out a `.csv` the system has
 * decided is `text/plain`, or `text/comma-separated-values`, and a greyed-out
 * file with no explanation is indistinguishable from a broken app.
 */
@Composable
actual fun rememberFilePicker(): FilePicker {
    val context = LocalContext.current

    // The result arrives on a later frame, after a round trip through another
    // app, so the callback cannot be a parameter of the launch.
    val pending = remember { mutableStateOf<((String?) -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val callback = pending.value
        pending.value = null
        if (uri == null) {
            // Cancelled, which is ordinary rather than an error.
            callback?.invoke(null)
            return@rememberLauncherForActivityResult
        }
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        }.getOrNull()
        callback?.invoke(text)
    }

    return remember(launcher) {
        FilePicker { onPicked ->
            pending.value = onPicked
            launcher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
        }
    }
}
