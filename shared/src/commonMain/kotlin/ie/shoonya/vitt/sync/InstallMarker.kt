package ie.shoonya.vitt.sync

/**
 * A value that identifies this *installation on this device* and does not
 * survive being restored onto another one.
 *
 * iOS supplies `identifierForVendor`, which is regenerated on a different
 * device; Android supplies `ANDROID_ID`, which is per app-signing-key per user
 * per device. Neither is used for tracking here — only to notice that the local
 * database arrived from somewhere else.
 */
expect fun installMarker(): String
