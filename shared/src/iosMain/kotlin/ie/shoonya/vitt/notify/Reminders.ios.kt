package ie.shoonya.vitt.notify

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

actual object Reminders {

    /**
     * One fixed id, so scheduling replaces rather than accumulates.
     *
     * A request with an existing identifier overwrites it, which is the wanted
     * behaviour: changing the time twice must not leave two reminders behind.
     */
    private const val ID = "vitt.daily.reminder"

    actual suspend fun requestPermission(): Boolean = suspendCancellableCoroutine { cont ->
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            // No badge. A number on the icon is a debt the app is claiming the
            // person owes it, and §5's Gentle tier rules that out.
            options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { granted, _ -> cont.resume(granted) }
    }

    actual fun apply(reminder: Reminder?) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(ID))
        if (reminder == null) return

        val content = UNMutableNotificationContent().apply {
            setTitle("Anything today?")
            // Says that "nothing" is a real answer, because it is: a no-spend
            // day still counts as a day recorded, so a streak is broken by
            // silence rather than by living cheaply.
            setBody("A few seconds. Nothing to report is an answer too.")
        }

        val components = NSDateComponents().apply {
            hour = reminder.hour.toLong()
            minute = reminder.minute.toLong()
        }

        center.addNotificationRequest(
            UNNotificationRequest.requestWithIdentifier(
                identifier = ID,
                content = content,
                trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                    dateComponents = components,
                    repeats = true,
                ),
            ),
        ) { }
    }
}
