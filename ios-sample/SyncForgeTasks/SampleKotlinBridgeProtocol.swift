import Foundation

/// Shared bridge contract — implemented by Kotlin ([SampleKotlinBridge]) or Swift E2E stub.
@MainActor
protocol SampleKotlinBridgeProtocol: AnyObject {
    func setStatusListener(_ listener: @escaping (String) -> Void)
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