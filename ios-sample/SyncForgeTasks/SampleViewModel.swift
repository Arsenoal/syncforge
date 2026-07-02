import Foundation
import SyncForgeSample

@MainActor
final class SampleViewModel: ObservableObject {
    @Published var tasks: [TaskRowModel] = []
    @Published var notes: [NoteRowModel] = []
    @Published var tags: [TagRowModel] = []
    @Published var statusLabel: String = "Idle"
    @Published var newTaskTitle: String = ""
    @Published var newNoteTitle: String = ""
    @Published var newNoteBody: String = ""
    @Published var newTagLabel: String = ""
    @Published var isSyncing: Bool = false
    @Published var errorMessage: String?

    private let resolvedBaseUrl: String
    private let e2eMode: Bool
    private var controller: IosSampleController?
    private var isStarted = false

    init(baseUrl: String? = nil) {
        resolvedBaseUrl = baseUrl
            ?? ProcessInfo.processInfo.environment["MOCK_SERVER_BASE_URL"]
            ?? SampleConfig.defaultBaseUrl
        e2eMode = ProcessInfo.processInfo.environment["E2E_TESTING"] == "1"
    }

    /// Lazily wires Kotlin/SyncForge on first user action — keeps launch idle for XCUITest.
    func startIfNeeded() {
        guard !isStarted else { return }
        isStarted = true

        let controller = IosSampleController(baseUrl: resolvedBaseUrl, e2eMode: e2eMode)
        self.controller = controller

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

        controller.setNotesListener { [weak self] items in
            DispatchQueue.main.async {
                self?.notes = KotlinInterop.mapNotes(items)
            }
        }

        controller.setTagsListener { [weak self] items in
            DispatchQueue.main.async {
                self?.tags = KotlinInterop.mapTags(items)
            }
        }
    }

    func addTask() {
        startIfNeeded()
        let title = newTaskTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty else {
            errorMessage = "Enter a task title"
            return
        }

        errorMessage = nil
        requireController().addTask(title: title) { [weak self] success, error in
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

    func addNote() {
        startIfNeeded()
        let title = newNoteTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty else {
            errorMessage = "Enter a note title"
            return
        }

        let body = newNoteBody.trimmingCharacters(in: .whitespacesAndNewlines)
        errorMessage = nil
        requireController().addNote(title: title, body: body) { [weak self] success, error in
            DispatchQueue.main.async {
                guard let self else { return }
                if KotlinInterop.bool(success) {
                    self.newNoteTitle = ""
                    self.newNoteBody = ""
                } else {
                    self.errorMessage = error ?? "Failed to add note"
                }
            }
        }
    }

    func addTag() {
        startIfNeeded()
        let label = newTagLabel.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !label.isEmpty else {
            errorMessage = "Enter a tag label"
            return
        }

        errorMessage = nil
        requireController().addTag(label: label) { [weak self] success, error in
            DispatchQueue.main.async {
                guard let self else { return }
                if KotlinInterop.bool(success) {
                    self.newTagLabel = ""
                } else {
                    self.errorMessage = error ?? "Failed to add tag"
                }
            }
        }
    }

    func deleteNote(_ note: NoteRowModel) {
        startIfNeeded()
        errorMessage = nil
        requireController().deleteNote(noteId: note.id) { [weak self] success, error in
            DispatchQueue.main.async {
                guard let self else { return }
                if !KotlinInterop.bool(success) {
                    self.errorMessage = error ?? "Failed to delete note"
                }
            }
        }
    }

    func deleteTag(_ tag: TagRowModel) {
        startIfNeeded()
        errorMessage = nil
        requireController().deleteTag(tagId: tag.id) { [weak self] success, error in
            DispatchQueue.main.async {
                guard let self else { return }
                if !KotlinInterop.bool(success) {
                    self.errorMessage = error ?? "Failed to delete tag"
                }
            }
        }
    }

    func sync() {
        startIfNeeded()
        errorMessage = nil
        isSyncing = true
        requireController().sync { [weak self] _, status in
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

    private func requireController() -> IosSampleController {
        guard let controller else {
            fatalError("SampleViewModel.startIfNeeded() must run before calling SyncForge APIs")
        }
        return controller
    }
}