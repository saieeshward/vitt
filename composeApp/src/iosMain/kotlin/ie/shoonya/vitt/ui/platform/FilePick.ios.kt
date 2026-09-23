package ie.shoonya.vitt.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeCommaSeparatedText
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject

/**
 * The document picker, which is what an iOS user reaches for to bring a file in.
 *
 * Two content types for the same reason Android lists three: a `.csv` handed
 * out by a bank is regularly typed as plain text, and a file greyed out in the
 * picker with no explanation is indistinguishable from a broken app.
 *
 * The delegate is held in a property rather than passed inline. `UIKit` keeps
 * only a weak reference to it, so a delegate created at the call site is
 * deallocated the moment the function returns and the picked file is delivered
 * to nothing at all — the same trap `BrowserAuth.ios.kt` documents for its
 * presentation anchor.
 */
@OptIn(ExperimentalForeignApi::class)
private class PickerDelegate : NSObject(), UIDocumentPickerDelegateProtocol {

    var onPicked: ((String?) -> Unit)? = null

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val callback = onPicked
        onPicked = null
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (url == null) {
            callback?.invoke(null)
            return
        }
        // No security-scoped access needed: the picker is opened with
        // asCopy, so this URL is already a copy inside the app's own
        // container. Claiming a scope we own would be pointless, and reading a
        // scoped file without claiming one returns nothing with no error.
        val text = NSString.stringWithContentsOfURL(url, NSUTF8StringEncoding, null)
        callback?.invoke(text)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        val callback = onPicked
        onPicked = null
        callback?.invoke(null)
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberFilePicker(): FilePicker {
    val delegate = remember { PickerDelegate() }
    return remember(delegate) {
        FilePicker { onPicked ->
            delegate.onPicked = onPicked
            val picker = UIDocumentPickerViewController(
                forOpeningContentTypes = listOf(UTTypeCommaSeparatedText, UTTypePlainText),
                // A copy in our own container, so the read needs no
                // security-scoped access and the original is never held open.
                asCopy = true,
            )
            picker.delegate = delegate
            // Presented from the key window's root controller, which is the
            // Compose view controller hosting the whole app.
            UIApplication.sharedApplication.keyWindow?.rootViewController
                ?.presentViewController(picker, animated = true, completion = null)
        }
    }
}
