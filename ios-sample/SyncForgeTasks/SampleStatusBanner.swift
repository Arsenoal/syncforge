import SwiftUI

struct SampleStatusBanner: View {
    @EnvironmentObject private var viewModel: SampleViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Text(viewModel.statusLabel)
                    .font(.subheadline)
                    .foregroundColor(
                        viewModel.statusLabel.localizedCaseInsensitiveContains("error") ? .red : .secondary
                    )
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityIdentifier("sync_status_label")

                AccessibleButton(
                    title: viewModel.isSyncing ? "Syncing…" : "Sync",
                    accessibilityIdentifier: "sync_button",
                    isEnabled: !viewModel.isSyncing,
                    action: viewModel.sync
                )
                .frame(minWidth: 44, minHeight: 44)
            }
            .accessibilityElement(children: .contain)

            if viewModel.hasConflicts {
                HStack(spacing: 6) {
                    Image(systemName: "exclamationmark.triangle.fill")
                    Text("Conflicts need resolution — sync after editing on server")
                }
                .font(.caption)
                .foregroundColor(.orange)
            }

            if let error = viewModel.errorMessage {
                Text(error)
                    .font(.caption)
                    .foregroundColor(.red)
            }
        }
        .padding(12)
        .background(Color(UIColor.secondarySystemBackground))
        .cornerRadius(10)
    }
}