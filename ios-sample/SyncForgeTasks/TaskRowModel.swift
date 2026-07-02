import Foundation

/// Swift-native task row — decoupled from Kotlin export edge cases.
struct TaskRowModel: Identifiable, Equatable {
    let id: String
    let title: String
    let completed: Bool
    let syncStateLabel: String
}