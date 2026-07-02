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

    func addNote() {
        let title = newNoteTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty else {
            errorMessage = "Enter a note title"
            return
        }

        let body = newNoteBody.trimmingCharacters(in: .whitespacesAndNewlines)
        errorMessage = nil
        controller.addNote(title: title, body: body) { [weak self] success, error in
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
        let label = newTagLabel.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !label.isEmpty else {
            errorMessage = "Enter a tag label"
            return
        }

        errorMessage = nil
        controller.addTag(label: label) { [weak self] success, error in
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
        errorMessage = nil
        controller.deleteNote(noteId: note.id) { [weak self] success, error in
            DispatchQueue.main.async {
                guard let self else { return }
                if !KotlinInterop.bool(success) {
                    self.errorMessage = error ?? "Failed to delete note"
                }
            }
        }
    }

    func deleteTag(_ tag: TagRowModel) {
        errorMessage = nil
        controller.deleteTag(tagId: tag.id) { [weak self] success, error in
            DispatchQueue.main.async {
                guard let self else { return }
                if !KotlinInterop.bool(success) {
                    self.errorMessage = error ?? "Failed to delete tag"
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