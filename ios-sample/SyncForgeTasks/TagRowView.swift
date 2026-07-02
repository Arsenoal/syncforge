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

                Text(SyncStateStyle.displayLabel(for: tag.syncStateLabel))
                    .font(.caption)
                    .foregroundColor(SyncStateStyle.color(for: tag.syncStateLabel))
            }

            Spacer()

            Button("Delete", action: onDelete)
                .font(.caption)
                .foregroundColor(.red)
        }
        .padding(.vertical, 4)
    }
}