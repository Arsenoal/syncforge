import SwiftUI

struct TasksView: View {
    @EnvironmentObject private var viewModel: SampleViewModel

    var body: some View {
        NavigationView {
            VStack(spacing: 12) {
                statusBanner
                addTaskSection
                taskList
            }
            .padding(.horizontal)
            .navigationTitle("SyncForge Tasks")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(viewModel.isSyncing ? "Syncing…" : "Sync") {
                        viewModel.sync()
                    }
                    .disabled(viewModel.isSyncing)
                }
            }
        }
        .navigationViewStyle(.stack)
    }

    private var statusBanner: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(viewModel.statusLabel)
                .font(.subheadline)
                .foregroundColor(
                    viewModel.statusLabel.localizedCaseInsensitiveContains("error") ? .red : .secondary
                )
                .frame(maxWidth: .infinity, alignment: .leading)

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

    private var addTaskSection: some View {
        HStack(spacing: 8) {
            TextField("New task", text: $viewModel.newTaskTitle, onCommit: {
                viewModel.addTask()
            })
            .textFieldStyle(RoundedBorderTextFieldStyle())

            Button("Add", action: viewModel.addTask)
        }
    }

    private var taskList: some View {
        Group {
            if viewModel.tasks.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "checklist")
                        .font(.largeTitle)
                        .foregroundColor(.secondary)
                    Text("No tasks yet")
                        .font(.headline)
                    Text("Add a task, then tap Sync to push to the mock server.")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List {
                    ForEach(viewModel.tasks) { task in
                        TaskRowView(task: task)
                    }
                }
                .listStyle(PlainListStyle())
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

#if DEBUG
struct TasksView_Previews: PreviewProvider {
    static var previews: some View {
        TasksView()
            .environmentObject(SampleViewModel())
    }
}
#endif