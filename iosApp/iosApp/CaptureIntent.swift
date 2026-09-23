import AppIntents
import ComposeApp

/// Logging a spend from outside the app: Siri, Spotlight, the Shortcuts app, or
/// an automation the user wrote themselves.
///
/// This is the compliant half of automatic capture. VITT never reads a
/// notification — Play's Sensitive Permissions policy forbids it and Apple
/// offers no API for it — but a person can build a Shortcuts automation on their
/// own device that fires on a bank alert and calls this. Their device, their
/// automation, their choice. The app only ever exposes the door.
///
/// `openAppWhenRun` is on because the parse is an offer, not a saved entry. The
/// amount lands on a filled-in sheet the user confirms. Saving silently in the
/// background would mean a misparsed sign becoming a real balance nobody
/// checked, which is the one failure `AmountParser` is written to avoid.
@available(iOS 16.0, *)
struct CaptureIntent: AppIntent {
    static var title: LocalizedStringResource = "Log a spend"

    static var description = IntentDescription(
        "Hands an amount to VITT. It opens filled in, for you to confirm.",
        categoryName: "Capture"
    )

    static var openAppWhenRun: Bool = true

    @Parameter(
        title: "Text",
        description: "An amount, or the text of a bank alert.",
        requestValueDialog: "What did you spend?"
    )
    var text: String

    @MainActor
    func perform() async throws -> some IntentResult {
        // Safe before the UI exists: an intent can launch the app, so this may
        // run before any view controller has registered a listener. IosCapture
        // holds the text and replays it when one does.
        IosCapture.shared.submit(text: text)
        return .result()
    }
}

/// Puts the intent in Shortcuts and Spotlight without the user going looking.
///
/// The phrases matter more than they look: a Shortcut nobody can remember how to
/// invoke is a Shortcut nobody uses, and the whole value of this surface is that
/// it is faster than opening the app.
@available(iOS 16.0, *)
struct VittShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: CaptureIntent(),
            phrases: [
                "Log a spend in \(.applicationName)",
                "Add to \(.applicationName)",
                "\(.applicationName) spend"
            ],
            shortTitle: "Log a spend",
            systemImageName: "plus.circle"
        )
    }
}
