#if !E2E_SWIFT_STUB
import Foundation
import SyncForgeSample

enum KotlinInterop {

    /// Kotlin/Native may export `Boolean` as `Bool` or `KotlinBoolean`.
    static func bool(_ value: Any?) -> Bool {
        if let value = value as? Bool {
            return value
        }
        if let value = value as? KotlinBoolean {
            return value.boolValue
        }
        return false
    }

    static func mapTasks(_ items: Any?) -> [TaskRowModel] {
        mapItems(items, as: TaskItem.self, map: mapTaskItem)
    }

    static func mapNotes(_ items: Any?) -> [NoteRowModel] {
        mapItems(items, as: NoteItem.self, map: mapNoteItem)
    }

    static func mapTags(_ items: Any?) -> [TagRowModel] {
        mapItems(items, as: TagItem.self, map: mapTagItem)
    }

    private static func mapItems<T, R>(
        _ items: Any?,
        as type: T.Type,
        map: (T) -> R
    ) -> [R] {
        guard let items else { return [] }

        if let typedItems = items as? [T] {
            return typedItems.map(map)
        }

        if let nsArray = items as? NSArray {
            return nsArray.compactMap { element in
                guard let item = element as? T else { return nil }
                return map(item)
            }
        }

        return []
    }

    private static func mapTaskItem(_ item: TaskItem) -> TaskRowModel {
        TaskRowModel(
            id: item.id,
            title: item.title,
            completed: bool(item.completed),
            syncStateLabel: item.syncStateLabel
        )
    }

    private static func mapNoteItem(_ item: NoteItem) -> NoteRowModel {
        NoteRowModel(
            id: item.id,
            title: item.title,
            body: item.body,
            syncStateLabel: item.syncStateLabel
        )
    }

    private static func mapTagItem(_ item: TagItem) -> TagRowModel {
        TagRowModel(
            id: item.id,
            label: item.label,
            syncStateLabel: item.syncStateLabel
        )
    }
}
#endif