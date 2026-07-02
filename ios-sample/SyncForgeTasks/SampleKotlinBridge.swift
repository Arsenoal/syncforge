import Foundation
import SyncForge
import SyncForgeSample

/// Kotlin/SyncForge entry point — kept in a separate file so [SampleViewModel] does not
/// load KMP frameworks until the first user action (XCUITest can query SwiftUI first).
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

@MainActor
final class SampleKotlinBridge: SampleKotlinBridgeProtocol {
    private static var didRegisterBackgroundSync = false

    private let controller: IosSampleController

    init(baseUrl: String, e2eMode: Bool) {
        if !e2eMode, !Self.didRegisterBackgroundSync {
            IosBackgroundSyncKt.registerIosBackgroundSyncTasks(
                taskIdentifier: SampleConfig.backgroundSyncTaskId
            )
            Self.didRegisterBackgroundSync = true
        }
        controller = IosSampleController(baseUrl: baseUrl, e2eMode: e2eMode)
    }

    func setStatusListener(_ listener: @escaping (String) -> Void) {
        controller.setStatusListener { label in
            listener(label)
        }
    }

    func setTasksListener(_ listener: @escaping ([TaskRowModel]) -> Void) {
        controller.setTasksListener { items in
            listener(KotlinInterop.mapTasks(items))
        }
    }

    func setNotesListener(_ listener: @escaping ([NoteRowModel]) -> Void) {
        controller.setNotesListener { items in
            listener(KotlinInterop.mapNotes(items))
        }
    }

    func setTagsListener(_ listener: @escaping ([TagRowModel]) -> Void) {
        controller.setTagsListener { items in
            listener(KotlinInterop.mapTags(items))
        }
    }

    func addTask(title: String, onComplete: @escaping (Bool, String?) -> Void) {
        controller.addTask(title: title) { success, error in
            onComplete(KotlinInterop.bool(success), error)
        }
    }

    func addNote(title: String, body: String, onComplete: @escaping (Bool, String?) -> Void) {
        controller.addNote(title: title, body: body) { success, error in
            onComplete(KotlinInterop.bool(success), error)
        }
    }

    func addTag(label: String, onComplete: @escaping (Bool, String?) -> Void) {
        controller.addTag(label: label) { success, error in
            onComplete(KotlinInterop.bool(success), error)
        }
    }

    func deleteNote(noteId: String, onComplete: @escaping (Bool, String?) -> Void) {
        controller.deleteNote(noteId: noteId) { success, error in
            onComplete(KotlinInterop.bool(success), error)
        }
    }

    func deleteTag(tagId: String, onComplete: @escaping (Bool, String?) -> Void) {
        controller.deleteTag(tagId: tagId) { success, error in
            onComplete(KotlinInterop.bool(success), error)
        }
    }

    func sync(onComplete: @escaping (Bool, String) -> Void) {
        controller.sync { success, status in
            onComplete(KotlinInterop.bool(success), status)
        }
    }
}