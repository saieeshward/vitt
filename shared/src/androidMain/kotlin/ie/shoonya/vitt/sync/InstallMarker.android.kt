package ie.shoonya.vitt.sync

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

private var appContext: Context? = null

fun initInstallMarker(context: Context) { appContext = context.applicationContext }

@SuppressLint("HardwareIds")
actual fun installMarker(): String {
    val ctx = appContext ?: return "uninitialised"
    return Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
        ?: "unknown-android-id"
}
