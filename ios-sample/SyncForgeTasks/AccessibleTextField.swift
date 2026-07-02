import SwiftUI
import UIKit

/// UITextField wrapper so XCUITest can query fields by `accessibilityIdentifier`.
struct AccessibleTextField: UIViewRepresentable {
    let placeholder: String
    @Binding var text: String
    let accessibilityIdentifier: String
    var onCommit: (() -> Void)?

    func makeUIView(context: Context) -> UITextField {
        let field = UITextField()
        field.placeholder = placeholder
        field.borderStyle = .roundedRect
        field.accessibilityIdentifier = accessibilityIdentifier
        field.delegate = context.coordinator
        field.addTarget(
            context.coordinator,
            action: #selector(Coordinator.textChanged(_:)),
            for: .editingChanged
        )
        return field
    }

    func updateUIView(_ uiView: UITextField, context: Context) {
        if uiView.text != text {
            uiView.text = text
        }
        uiView.placeholder = placeholder
        uiView.accessibilityIdentifier = accessibilityIdentifier
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(text: $text, onCommit: onCommit)
    }

    final class Coordinator: NSObject, UITextFieldDelegate {
        @Binding var text: String
        let onCommit: (() -> Void)?

        init(text: Binding<String>, onCommit: (() -> Void)?) {
            _text = text
            self.onCommit = onCommit
        }

        @objc func textChanged(_ sender: UITextField) {
            text = sender.text ?? ""
        }

        func textFieldShouldReturn(_ textField: UITextField) -> Bool {
            onCommit?()
            textField.resignFirstResponder()
            return true
        }
    }
}