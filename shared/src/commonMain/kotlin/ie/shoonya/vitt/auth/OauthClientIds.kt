package ie.shoonya.vitt.auth

/**
 * The OAuth client id for this platform.
 *
 * iOS and Android need separate clients because Google binds each to its
 * platform identity — bundle id on iOS, package name plus signing certificate
 * on Android. That binding is what makes an embedded client id safe to publish.
 */
expect fun platformClientId(): String
