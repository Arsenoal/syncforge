import SwiftUI

enum SyncStateStyle {
    static func displayLabel(for rawLabel: String) -> String {
        switch rawLabel.uppercased() {
        case "SYNCED": return "Synced"
        case "PENDING": return "Pending"
        case "CONFLICT": return "Conflict — tap Resolve"
        case "FAILED": return "Failed"
        default: return rawLabel
        }
    }

    static func color(for label: String) -> Color {
        switch label.uppercased() {
        case "SYNCED": return .green
        case "PENDING": return .orange
        case "FAILED", "CONFLICT": return .red
        default: return .secondary
        }
    }
}