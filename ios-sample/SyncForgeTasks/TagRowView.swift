import SwiftUI

struct TagRowView: View {
    let tag: TagRowModel
    let onDelete: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "tag.fill")
                .foregroundColor(.accentColor)

            VStack(alignment: .leading, spacing: 4) {
                Text(tag.label)
                    .font(.body)

                Text(tag.syncStateLabel)
                    .font(.caption)
                    .foregroundColor(SyncStateStyle.color(for: tag.syncStateLabel))
            }

            Spacer()

            Button("Delete", role: .destructive, action: onDelete)
                .font(.caption)
        }
        .padding(.vertical, 4)
    }
}