import SwiftUI
import UIKit

/// UIButton wrapper so XCUITest can query controls by `accessibilityIdentifier` outside TabView subtrees.
struct AccessibleButton: UIViewRepresentable {
    let title: String
    let accessibilityIdentifier: String
    let isEnabled: Bool
    let action: () -> Void

    func makeUIView(context: Context) -> UIButton {
        let button = UIButton(type: .system)
        button.accessibilityIdentifier = accessibilityIdentifier
        button.isAccessibilityElement = true
        button.addTarget(context.coordinator, action: #selector(Coordinator.tapped), for: .touchUpInside)
        return button
    }

    func updateUIView(_ uiView: UIButton, context: Context) {
        uiView.setTitle(title, for: .normal)
        uiView.accessibilityIdentifier = accessibilityIdentifier
        uiView.isEnabled = isEnabled
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(action: action)
    }

    final class Coordinator: NSObject {
        let action: () -> Void

        init(action: @escaping () -> Void) {
            self.action = action
        }

        @objc func tapped() {
            action()
        }
    }
}