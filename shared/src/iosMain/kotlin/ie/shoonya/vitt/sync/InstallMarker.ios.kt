package ie.shoonya.vitt.sync

import platform.UIKit.UIDevice

actual fun installMarker(): String =
    UIDevice.currentDevice.identifierForVendor?.UUIDString ?: "unknown-vendor-id"
