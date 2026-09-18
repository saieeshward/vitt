package ie.shoonya.vitt.ui.platform

import androidx.compose.runtime.Composable
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

@Composable
actual fun rememberLinkOpener(): (String) -> Unit = { url ->
    NSURL.URLWithString(url)?.let { UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null) }
}
