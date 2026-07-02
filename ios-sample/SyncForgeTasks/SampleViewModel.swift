import Foundation

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
    /// Set after [startIfNeeded] finishes — exposed for XCUITest bridge warmup.
    @Published private(set) var kotlinBridgeReady: Bool = false

    private let resolvedBaseUrl: String
    private let e2eMode: Bool
    private var bridge: SampleKotlinBridgeProtocol?
    private var isPreloadingBridge = false

    init(baseUrl: String? = nil) {
        resolvedBaseUrl = baseUrl
            ?? ProcessInfo.processInfo.environment["MOCK_SERVER_BASE_URL"]
            ?? SampleConfig.defaultBaseUrl
        e2eMode = SampleConfig.isE2eTesting
    }

    /// Kicks off Kotlin preload after SwiftUI is on screen (CI / XCUITest).
    func beginKotlinPreloadIfNeeded() {
        guard e2eMode else { return }
#if E2E_SWIFT_STUB
        startIfNeeded()
#else
        Task { @MainActor in
            startIfNeeded()
        }
#endif
    }

    /// Lazily loads Kotlin/SyncForge on first user action — keeps cold launch SwiftUI-only for XCUITest.
    func startIfNeeded() {
        guard bridge == nil else {
            kotlinBridgeReady = true
            return
        }
        guard !isPreloadingBridge else { return }
        isPreloadingBridge = true

        let bridge = makeBridge()
        self.bridge = bridge

        bridge.setStatusListener { [weak self] label in
            self?.statusLabel = label
            self?.isSyncing = label.localizedCaseInsensitiveContains("syncing")
        }

        bridge.setTasksListener { [weak self] items in
            self?.tasks = items
        }

        bridge.setNotesListener { [weak self] items in
            self?.notes = items
        }

        bridge.setTagsListener { [weak self] items in
            self?.tags = items
        }

        kotlinBridgeReady = true
    }

    func addTask() {
        startIfNeeded()
        let title = newTaskTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty else {
            errorMessage = "Enter a task title"
            return
        }

        errorMessage = nil
        requireBridge().addTask(title: title) { [weak self] success, error in
            guard let self else { return }
            if success {
                self.newTaskTitle = ""
            } else {
                self.errorMessage = error ?? "Failed to add task"
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
        requireBridge().addNote(title: title, body: body) { [weak self] success, error in
            guard let self else { return }
            if success {
                self.newNoteTitle = ""
                self.newNoteBody = ""
            } else {
                self.errorMessage = error ?? "Failed to add note"
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
        requireBridge().addTag(label: label) { [weak self] success, error in
            guard let self else { return }
            if success {
                self.newTagLabel = ""
            } else {
                self.errorMessage = error ?? "Failed to add tag"
            }
        }
    }

    func deleteNote(_ note: NoteRowModel) {
        startIfNeeded()
        errorMessage = nil
        requireBridge().deleteNote(noteId: note.id) { [weak self] success, error in
            guard let self else { return }
            if !success {
                self.errorMessage = error ?? "Failed to delete note"
            }
        }
    }

    func deleteTag(_ tag: TagRowModel) {
        startIfNeeded()
        errorMessage = nil
        requireBridge().deleteTag(tagId: tag.id) { [weak self] success, error in
            guard let self else { return }
            if !success {
                self.errorMessage = error ?? "Failed to delete tag"
            }
        }
    }

    func sync() {
        startIfNeeded()
        errorMessage = nil
        isSyncing = true
        requireBridge().sync { [weak self] _, status in
            guard let self else { return }
            self.isSyncing = status.localizedCaseInsensitiveContains("syncing")
            self.statusLabel = status
        }
    }

    var hasConflicts: Bool {
        statusLabel.localizedCaseInsensitiveContains("conflict")
    }

    private func makeBridge() -> SampleKotlinBridgeProtocol {
#if E2E_SWIFT_STUB
        SampleKotlinBridgeE2eStub(baseUrl: resolvedBaseUrl)
#else
        SampleKotlinBridge(baseUrl: resolvedBaseUrl, e2eMode: e2eMode)
#endif
    }

    private func requireBridge() -> SampleKotlinBridgeProtocol {
        guard let bridge else {
            fatalError("SampleViewModel.startIfNeeded() must run before calling SyncForge APIs")
        }
        return bridge
    }
}