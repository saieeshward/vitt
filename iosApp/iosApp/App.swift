import SwiftUI
import UIKit
import ComposeApp

/// Hosts the shared Compose UI. Everything visible on screen is drawn by
/// Compose Multiplatform; this file exists only to give iOS a UIViewController
/// to present, and should stay this small.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct VITTApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                // Every safe area is ceded to Compose, which applies the insets
                // itself (VittApp) and does its own keyboard avoidance. Keeping
                // the safe area here stopped the app at a hard line below the
                // status bar and above the home indicator, with a band of the
                // system's colour beyond it rather than the app's ground.
                .ignoresSafeArea()
                // The widget's "Log a spend" button. Without this the button
                // opened the app and did nothing, which is the one thing the
                // widget exists to do.
                .onOpenURL { url in
                    if url.scheme == "vitt" && url.host == "add" {
                        IosCapture.shared.openAdd()
                    }
                }
        }
    }
}
