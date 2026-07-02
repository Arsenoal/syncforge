import Foundation
import SyncForgeSample

@MainActor
final class SampleViewModel: ObservableObject {
    @Published var tasks: [TaskRowModel] = []
    @Published var statusLabel: String = "Idle"
    @Published var newTaskTitle: String = ""
    @Published var isSyncing: Bool = false
    @Published var errorMessage: String?

    private let controller: IosSampleController

    init(baseUrl: String = SampleConfig.defaultBaseUrl) {
        controller = IosSampleController(baseUrl: baseUrl)

        controller.setStatusListener { [weak self] label in
            DispatchQueue.main.async {
                self?.statusLabel = label
                self?.isSyncing = label.localizedCaseInsensitiveContains("syncing")
            }
        }

        controller.setTasksListener { [weak self] items in
            DispatchQueue.main.async {
                self?.tasks = KotlinInterop.mapTasks(items)
            }
        }
    }

    func addTask() {
        let title = newTaskTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty else {
            errorMessage = "Enter a task title"
            return
        }

        errorMessage = nil
        controller.addTask(title: title) { [weak self] success, error in
            DispatchQueue.main.async {
                guard let self else { return }
                if KotlinInterop.bool(success) {
                    self.newTaskTitle = ""
                } else {
                    self.errorMessage = error ?? "Failed to add task"
                }
            }
        }
    }

    func sync() {
        errorMessage = nil
        isSyncing = true
        controller.sync { [weak self] _, status in
            DispatchQueue.main.async {
                guard let self else { return }
                self.isSyncing = status.localizedCaseInsensitiveContains("syncing")
                self.statusLabel = status
            }
        }
    }

    var hasConflicts: Bool {
        statusLabel.localizedCaseInsensitiveContains("conflict")
    }
}