package ie.shoonya.vitt

/**
 * The door Swift knocks on when text arrives from outside the app.
 *
 * A Shortcut, a share extension, or anything else the user wires up hands text
 * here and the Compose side offers it for confirmation. Kept as an object with
 * a settable callback rather than a parameter on `MainViewController`, because
 * an App Intent can fire before any view controller exists: iOS launches the
 * app to run it, and the intent has no reference to the composition that has
 * not happened yet.
 *
 * The app reads nothing on its own. Everything that arrives here does so
 * because a person, or an automation a person wrote on their own device, chose
 * to send it. That distinction is the whole reason this is allowed to exist:
 * `PLAN.md` §0 rules out notification reading, and §6 explicitly keeps
 * user-authored Shortcuts automations, because exposing an intent is not the
 * same as going and taking the data.
 *
 * [pending] covers the cold-launch race. An intent that starts the app runs
 * before `MainViewController` has set [onText], so the text is held and
 * replayed the moment the callback lands, instead of being dropped into a
 * listener nobody has registered yet.
 */
object IosCapture {

    private var pending: String? = null

    var onText: ((String) -> Unit)? = null
        set(value) {
            field = value
            pending?.let { held ->
                pending = null
                value?.invoke(held)
            }
        }

    /** Called from Swift. Safe before the UI exists. */
    fun submit(text: String) {
        if (text.isBlank()) return
        val sink = onText
        if (sink == null) pending = text else sink(text)
    }
}
