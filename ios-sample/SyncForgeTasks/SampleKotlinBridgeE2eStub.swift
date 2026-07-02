#if E2E_SWIFT_STUB
import Foundation

/// Pure-Swift SyncForge stand-in for XCUITest CI — no KMP frameworks linked.
@MainActor
final class SampleKotlinBridgeE2eStub: SampleKotlinBridgeProtocol {
    private let baseUrl: String
    private var statusListener: ((String) -> Void)?
    private var tasksListener: (([TaskRowModel]) -> Void)?
    private var notesListener: (([NoteRowModel]) -> Void)?
    private var tagsListener: (([TagRowModel]) -> Void)?

    private var tasks: [TaskRowModel] = []
    private var notes: [NoteRowModel] = []
    private var tags: [TagRowModel] = []
    private var nextOutboxId: Int64 = 1

    private struct PendingEntry: Encodable {
        let id: Int64
        let entityType: String
        let entityId: String
        let changeType: String
        let payloadJson: String
        let localVersion: Int64
        let createdAtMillis: Int64
    }

    private struct PushRequest: Encodable {
        let entries: [PendingEntry]
    }

    private struct PushResponse: Decodable {
        let acknowledgedIds: [Int64]
    }

    private var pendingEntries: [PendingEntry] = []

    init(baseUrl: String) {
        self.baseUrl = baseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
    }

    func setStatusListener(_ listener: @escaping (String) -> Void) {
        statusListener = listener
        listener("Idle")
    }

    func setTasksListener(_ listener: @escaping ([TaskRowModel]) -> Void) {
        tasksListener = listener
        listener(tasks)
    }

    func setNotesListener(_ listener: @escaping ([NoteRowModel]) -> Void) {
        notesListener = listener
        listener(notes)
    }

    func setTagsListener(_ listener: @escaping ([TagRowModel]) -> Void) {
        tagsListener = listener
        listener(tags)
    }

    func addTask(title: String, onComplete: @escaping (Bool, String?) -> Void) {
        let id = UUID().uuidString
        tasks.append(TaskRowModel(id: id, title: title, completed: false, syncStateLabel: "PENDING"))
        enqueueTask(entityId: id, title: title)
        tasksListener?(tasks)
        onComplete(true, nil)
    }

    func addNote(title: String, body: String, onComplete: @escaping (Bool, String?) -> Void) {
        let id = UUID().uuidString
        notes.append(NoteRowModel(id: id, title: title, body: body, syncStateLabel: "PENDING"))
        enqueueNote(entityId: id, title: title, body: body)
        notesListener?(notes)
        onComplete(true, nil)
    }

    func addTag(label: String, onComplete: @escaping (Bool, String?) -> Void) {
        let id = UUID().uuidString
        tags.append(TagRowModel(id: id, label: label, syncStateLabel: "PENDING"))
        enqueueTag(entityId: id, label: label)
        tagsListener?(tags)
        onComplete(true, nil)
    }

    func deleteNote(noteId: String, onComplete: @escaping (Bool, String?) -> Void) {
        notes.removeAll { $0.id == noteId }
        notesListener?(notes)
        onComplete(true, nil)
    }

    func deleteTag(tagId: String, onComplete: @escaping (Bool, String?) -> Void) {
        tags.removeAll { $0.id == tagId }
        tagsListener?(tags)
        onComplete(true, nil)
    }

    func sync(onComplete: @escaping (Bool, String) -> Void) {
        statusListener?("Syncing")
        Task {
            do {
                try await pushPending()
                await MainActor.run {
                    self.markAllSynced()
                    let status = "Up to date"
                    self.statusListener?(status)
                    onComplete(true, status)
                }
            } catch {
                let message = error.localizedDescription
                await MainActor.run {
                    self.statusListener?("Error: \(message)")
                    onComplete(false, "Error: \(message)")
                }
            }
        }
    }

    private struct TaskPayload: Encodable {
        let id: String
        let title: String
        let completed: Bool
    }

    private struct NotePayload: Encodable {
        let id: String
        let title: String
        let body: String
    }

    private struct TagPayload: Encodable {
        let id: String
        let label: String
    }

    private func enqueueTask(entityId: String, title: String) {
        enqueue(
            entityType: "tasks",
            entityId: entityId,
            payloadJson: encodePayload(TaskPayload(id: entityId, title: title, completed: false))
        )
    }

    private func enqueueNote(entityId: String, title: String, body: String) {
        enqueue(
            entityType: "notes",
            entityId: entityId,
            payloadJson: encodePayload(NotePayload(id: entityId, title: title, body: body))
        )
    }

    private func enqueueTag(entityId: String, label: String) {
        enqueue(
            entityType: "tags",
            entityId: entityId,
            payloadJson: encodePayload(TagPayload(id: entityId, label: label))
        )
    }

    private func encodePayload<T: Encodable>(_ payload: T) -> String {
        let data = (try? JSONEncoder().encode(payload)) ?? Data()
        return String(data: data, encoding: .utf8) ?? "{}"
    }

    private func enqueue(entityType: String, entityId: String, payloadJson: String) {
        let entry = PendingEntry(
            id: nextOutboxId,
            entityType: entityType,
            entityId: entityId,
            changeType: "CREATE",
            payloadJson: payloadJson,
            localVersion: 1,
            createdAtMillis: Int64(Date().timeIntervalSince1970 * 1000)
        )
        nextOutboxId += 1
        pendingEntries.append(entry)
    }

    private func markAllSynced() {
        tasks = tasks.map { TaskRowModel(id: $0.id, title: $0.title, completed: $0.completed, syncStateLabel: "SYNCED") }
        notes = notes.map { NoteRowModel(id: $0.id, title: $0.title, body: $0.body, syncStateLabel: "SYNCED") }
        tags = tags.map { TagRowModel(id: $0.id, label: $0.label, syncStateLabel: "SYNCED") }
        pendingEntries.removeAll()
        tasksListener?(tasks)
        notesListener?(notes)
        tagsListener?(tags)
    }

    private func pushPending() async throws {
        guard !pendingEntries.isEmpty else { return }
        guard let url = URL(string: "\(baseUrl)/sync/push") else {
            throw URLError(.badURL)
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(PushRequest(entries: pendingEntries))

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
        let pushResponse = try JSONDecoder().decode(PushResponse.self, from: data)
        guard pushResponse.acknowledgedIds.count == pendingEntries.count else {
            throw URLError(.cannotParseResponse)
        }
    }
}
#endif