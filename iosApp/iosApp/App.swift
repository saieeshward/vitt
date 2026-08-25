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
                // Only the keyboard inset is ceded to Compose, which does its own
                // keyboard avoidance. Ignoring *all* safe areas draws content
                // under the Dynamic Island and the home indicator.
                .ignoresSafeArea(.keyboard)
        }
    }
}
