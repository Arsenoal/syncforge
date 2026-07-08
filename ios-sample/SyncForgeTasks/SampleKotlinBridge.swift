#if !E2E_SWIFT_STUB
import Foundation
import SyncForge
import SyncForgeSample

/// Kotlin/SyncForge entry point — not linked in E2E_SWIFT_STUB CI builds.
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

    func startObservingStatusLabels(onUpdate: @escaping (String) -> Void) -> Task<Void, Never> {
        KotlinFlowInterop.collect(controller.observeStatusLabel(), onUpdate: onUpdate)
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
#endif