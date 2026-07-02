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
        guard let items else { return [] }

        if let taskItems = items as? [TaskItem] {
            return taskItems.map(mapTaskItem)
        }

        if let nsArray = items as? NSArray {
            return nsArray.compactMap { element in
                guard let task = element as? TaskItem else { return nil }
                return mapTaskItem(task)
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
}