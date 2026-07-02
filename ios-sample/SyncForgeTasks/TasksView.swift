import SwiftUI

struct TasksView: View {
    @EnvironmentObject private var viewModel: SampleViewModel

    var body: some View {
        VStack(spacing: 12) {
            addTaskSection
            taskList
        }
        .padding(.horizontal)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var addTaskSection: some View {
        HStack(spacing: 8) {
            TextField("New task", text: $viewModel.newTaskTitle, onCommit: {
                viewModel.addTask()
            })
            .textFieldStyle(RoundedBorderTextFieldStyle())
            .accessibilityIdentifier("new_task_input")

            Button("Add", action: viewModel.addTask)
                .accessibilityIdentifier("add_task_button")
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