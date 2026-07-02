import SwiftUI

struct TaskRowView: View {
    let task: TaskRowModel

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: task.completed ? "checkmark.circle.fill" : "circle")
                .foregroundColor(task.completed ? .green : .secondary)

            VStack(alignment: .leading, spacing: 4) {
                Text(task.title)
                    .font(.body)

                Text(task.syncStateLabel)
                    .font(.caption)
                    .foregroundColor(syncStateColor)
            }

            Spacer()
        }
        .padding(.vertical, 4)
    }

    private var syncStateColor: Color {
        switch task.syncStateLabel.uppercased() {
        case "SYNCED": return .green
        case "PENDING": return .orange
        case "FAILED": return .red
        default: return .secondary
        }
    }
}