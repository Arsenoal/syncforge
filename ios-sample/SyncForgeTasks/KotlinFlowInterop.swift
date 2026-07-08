#if !E2E_SWIFT_STUB
import Foundation

/// Helpers for collecting SKIE-exported Kotlin `Flow` values in Swift/SwiftUI.
enum KotlinFlowInterop {

    /// Collects an `AsyncSequence` (SKIE Flow) until cancelled, delivering each element on the main actor.
    static func collect<Sequence: AsyncSequence>(
        _ sequence: Sequence,
        onUpdate: @escaping @MainActor (Sequence.Element) -> Void
    ) -> Task<Void, Never> {
        Task {
            do {
                for try await value in sequence {
                    await onUpdate(value)
                }
            } catch is CancellationError {
                return
            } catch {
                #if DEBUG
                print("Kotlin Flow collection ended: \(error)")
                #endif
            }
        }
    }
}
#endif