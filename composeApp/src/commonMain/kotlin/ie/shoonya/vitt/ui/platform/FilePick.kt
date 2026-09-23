package ie.shoonya.vitt.ui.platform

import androidx.compose.runtime.Composable

/**
 * Takes a file *in*, which is the mirror of [FileExporter].
 *
 * Text rather than a path or a stream, for the reason the exporter passes text
 * out: nothing here writes to durable storage, and holding a path would mean
 * the app keeping a handle on a document it does not own. A bank export is a
 * few hundred kilobytes read once and parsed; there is nothing to stream.
 *
 * The callback takes null for a cancelled pick, which is the common case and
 * not an error. Somebody opening the picker to see what is on their phone and
 * changing their mind should land back where they were, with nothing said.
 */
fun interface FilePicker {
    /** Opens the platform picker. [onPicked] gets the file's text, or null. */
    fun pick(onPicked: (String?) -> Unit)
}

/**
 * Composable for the same reason [rememberFileExporter] is: Android needs an
 * activity-result launcher and iOS needs the window's root view controller, and
 * neither exists outside composition.
 */
@Composable
expect fun rememberFilePicker(): FilePicker
