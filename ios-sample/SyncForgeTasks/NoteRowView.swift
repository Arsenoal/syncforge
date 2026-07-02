import SwiftUI

struct NoteRowView: View {
    let note: NoteRowModel
    let onDelete: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(note.title)
                    .font(.body)

                if !note.body.isEmpty {
                    Text(note.body)
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .lineLimit(2)
                }

                Text(SyncStateStyle.displayLabel(for: note.syncStateLabel))
                    .font(.caption)
                    .foregroundColor(SyncStateStyle.color(for: note.syncStateLabel))
            }

            Spacer()

            Button("Delete", action: onDelete)
                .font(.caption)
                .foregroundColor(.red)
        }
        .padding(.vertical, 4)
    }
}