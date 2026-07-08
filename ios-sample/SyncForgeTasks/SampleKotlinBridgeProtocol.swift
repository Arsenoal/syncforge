import Foundation

/// Shared bridge contract — implemented by Kotlin ([SampleKotlinBridge]) or Swift E2E stub.
@MainActor
protocol SampleKotlinBridgeProtocol: AnyObject {
    /// Long-lived status observation. Cancel the returned task when the owner is deallocated.
    func startObservingStatusLabels(onUpdate: @escaping (String) -> Void) -> Task<Void, Never>
    func setTasksListener(_ listener: @escaping ([TaskRowModel]) -> Void)
    func setNotesListener(_ listener: @escaping ([NoteRowModel]) -> Void)
    func setTagsListener(_ listener: @escaping ([TagRowModel]) -> Void)
    func addTask(title: String, onComplete: @escaping (Bool, String?) -> Void)
    func addNote(title: String, body: String, onComplete: @escaping (Bool, String?) -> Void)
    func addTag(label: String, onComplete: @escaping (Bool, String?) -> Void)
    func deleteNote(noteId: String, onComplete: @escaping (Bool, String?) -> Void)
    func deleteTag(tagId: String, onComplete: @escaping (Bool, String?) -> Void)
    func sync(onComplete: @escaping (Bool, String) -> Void)
}