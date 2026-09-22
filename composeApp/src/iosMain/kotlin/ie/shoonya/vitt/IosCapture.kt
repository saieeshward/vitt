package ie.shoonya.vitt

import ie.shoonya.vitt.capture.PendingHandoff

/**
 * The door Swift knocks on when something arrives from outside the app.
 *
 * A Shortcut, a share extension, the widget's button. Kept as an object with
 * settable callbacks rather than parameters on `MainViewController`, because
 * any of these can fire *before* a view controller exists: iOS launches the app
 * to run them, and there is no composition yet to hand them to.
 *
 * The app reads nothing on its own. Everything here arrives because a person,
 * or an automation a person wrote on their own device, chose to send it. That
 * distinction is why this is allowed to exist at all: `PLAN.md` §0 rules out
 * notification reading, and §6 keeps user-authored Shortcuts automations,
 * because exposing a door is not the same as going and taking the data.
 *
 * Both signals use [PendingHandoff], which holds a payload until a listener
 * registers and delivers it exactly once. That is tested in `:shared`, and it
 * is the piece that was actually wrong first time: the widget's button opened
 * the app and did nothing, because the tap landed before anybody was listening.
 */
object IosCapture {

    private val text = PendingHandoff<String>()
    private val add = PendingHandoff<Unit>()

    /** Set once the UI exists. Registering drains anything that arrived first. */
    var onText: ((String) -> Unit)?
        get() = text.listener
        set(value) { text.listener = value }

    var onOpenAdd: (() -> Unit)?
        get() = add.listener?.let { sink -> { sink(Unit) } }
        set(value) { add.listener = value?.let { sink -> { _: Unit -> sink() } } }

    /** Called from Swift with shared text. Safe before the UI exists. */
    fun submit(text: String) {
        if (text.isBlank()) return
        this.text.offer(text)
    }

    /** Called from Swift when a `vitt://add` link arrives. Carries no payload. */
    fun openAdd() {
        add.offer(Unit)
    }
}
