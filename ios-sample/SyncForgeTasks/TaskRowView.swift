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
                    .foregroundColor(SyncStateStyle.color(for: task.syncStateLabel))
            }

            Spacer()
        }
        .padding(.vertical, 4)
    }
}