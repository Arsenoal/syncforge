import Foundation

struct NoteRowModel: Identifiable, Equatable {
    let id: String
    let title: String
    let body: String
    let syncStateLabel: String
}