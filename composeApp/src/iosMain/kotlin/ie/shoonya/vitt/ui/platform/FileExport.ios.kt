package ie.shoonya.vitt.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.popoverPresentationController

/**
 * The share sheet, which is what an iOS user reaches for to get a file out.
 *
 * All the files go into **one** sheet. Presenting a second controller while the
 * first is up does nothing at all, so calling this once per file loses every
 * file after the first with no error anywhere.
 *
 * Written into `NSTemporaryDirectory()` rather than Documents: the system
 * reclaims it, and the app has no business keeping a second copy of the user's
 * ledger with no lifecycle attached. The share sheet reads the files before the
 * user dismisses it, so a temporary path is enough.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberFileExporter(): FileExporter = remember {
    FileExporter { files ->
        if (files.isEmpty()) return@FileExporter
        val urls = files.map { file ->
            val path = NSTemporaryDirectory() + file.name
            @Suppress("CAST_NEVER_SUCCEEDS")
            (file.content as NSString).writeToFile(
                path = path,
                atomically = true,
                encoding = NSUTF8StringEncoding,
                error = null,
            )
            NSURL.fileURLWithPath(path)
        }
        val sheet = UIActivityViewController(
            activityItems = urls,
            applicationActivities = null,
        )
        // Presented from the key window's root controller, which is the Compose
        // view controller: Compose Multiplatform on iOS is hosted inside one, so
        // there is always something to present from.
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return@FileExporter
        // On iPad a share sheet is a popover and UIKit throws if it has nothing
        // to point at. The build has always been universal, so Export crashed
        // on every iPad. Anchored to the middle of the window with no arrow,
        // which is how a sheet with no button of its own is shown there; on a
        // phone this controller is null and the sheet is the usual one.
        sheet.popoverPresentationController?.let { popover ->
            popover.sourceView = root.view
            popover.sourceRect = root.view.bounds.useContents {
                CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0)
            }
            popover.permittedArrowDirections = 0uL
        }
        root.presentViewController(sheet, animated = true, completion = null)
    }
}
