package ie.shoonya.vitt.capture

/**
 * Something handed in from outside, held until there is somebody to hand it to.
 *
 * Every capture surface has the same race. A share, a Shortcut or a widget tap
 * *starts* the app, so the payload arrives before any of the UI exists and
 * before anything has registered a listener. Delivering it straight into a null
 * callback loses it silently, and the person who tapped the widget sees the app
 * open and do nothing — which is exactly the bug this was written after
 * finding.
 *
 * So the value waits. Registering a listener drains whatever is held, and the
 * handoff is empty again afterwards: this is a one-shot delivery, not a
 * subscription, because replaying an old share every time the UI is recreated
 * would reopen a sheet the user already dismissed.
 */
class PendingHandoff<T : Any> {

    private var held: T? = null

    var listener: ((T) -> Unit)? = null
        set(value) {
            field = value
            // Drain on registration, and clear before invoking: a listener that
            // synchronously re-enters must not find the same value still here.
            val pending = held
            if (pending != null && value != null) {
                held = null
                value(pending)
            }
        }

    /** Deliver now if anybody is listening, otherwise hold. */
    fun offer(value: T) {
        val sink = listener
        if (sink == null) held = value else sink(value)
    }

    /** What is waiting, for tests and for diagnostics. Null once drained. */
    val pending: T? get() = held
}
