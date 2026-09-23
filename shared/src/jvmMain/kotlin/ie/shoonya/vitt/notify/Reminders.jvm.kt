package ie.shoonya.vitt.notify

/** No notifications on the JVM. This target exists to run the shared tests. */
actual object Reminders {
    actual suspend fun requestPermission(): Boolean = false
    actual fun apply(reminder: Reminder?) = Unit
}
