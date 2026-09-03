package ie.shoonya.vitt.ui.platform

import androidx.compose.runtime.Composable

/** One generated file, held in memory only for as long as the handoff takes. */
data class ExportFile(
    /** Suggested name, extension included. The user may change it. */
    val name: String,
    val content: String,
)

/**
 * Hands generated files to the platform, so they can leave the app.
 *
 * **Takes a list, not one file.** An export is two files, because a transfer has
 * two amounts and two accounts and would leave half of every transaction row
 * blank in a combined sheet. Calling a single-file exporter twice in a row does
 * not work and fails *silently*: iOS already has a view controller presented, so
 * the second share sheet is dropped and the user is never told the transfers
 * file existed. Found in the simulator, not in review.
 *
 * The two platforms deliberately do different things, because "get these files
 * out of here" has a different idiomatic answer on each. iOS presents one share
 * sheet holding both files; Android walks the system create-document picker once
 * per file, since that picker takes one destination at a time. Forcing one
 * metaphor onto both would mean a share sheet on Android, which needs a
 * `FileProvider`, a manifest entry and an XML paths file, for a worse result
 * than the picker the platform already has.
 *
 * Content is passed as strings rather than paths because nothing here writes to
 * durable storage. A file the app kept a copy of would be a second store of the
 * user's data with no lifecycle, which is the opposite of what an export is for.
 */
fun interface FileExporter {
    fun offer(files: List<ExportFile>)
}

/**
 * Composable because both platforms need something that only exists inside
 * composition: Android an activity-result launcher, iOS the current window's
 * root view controller.
 */
@Composable
expect fun rememberFileExporter(): FileExporter
