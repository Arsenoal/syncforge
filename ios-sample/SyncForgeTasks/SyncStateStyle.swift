import SwiftUI

enum SyncStateStyle {
    static func color(for label: String) -> Color {
        switch label.uppercased() {
        case "SYNCED": return .green
        case "PENDING": return .orange
        case "FAILED": return .red
        default: return .secondary
        }
    }
}