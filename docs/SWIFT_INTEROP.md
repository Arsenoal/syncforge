# Swift interop guide (SKIE)

Recommended patterns for calling SyncForge Kotlin APIs from Swift and SwiftUI.

**Prerequisites:** [IOS_SETUP.md](IOS_SETUP.md) — framework build, `SyncForge.ios { }`, Xcode wiring.

---

## SKIE configuration

[SKIE](https://skie.touchlab.co/) is applied when linking iOS frameworks. SyncForge enables **Flow** and **suspend** interop for public API packages:

| Module | SKIE groups | Swift import |
|--------|-------------|--------------|
| `:syncforge` | `dev.syncforge`, `dev.syncforge.sample` | `import SyncForge` |
| `:sample-ios-shared` | `dev.syncforge.sample.ios` | `import SyncForgeSample` |

Config lives in Gradle:

- [`syncforge/build.gradle.kts`](../syncforge/build.gradle.kts) — `skie { features { group("dev.syncforge") { … } } }`
- [`sample-ios-shared/build.gradle.kts`](../sample-ios-shared/build.gradle.kts) — `dev.syncforge.sample.ios`

After upgrading Kotlin, SKIE, or changing exported Kotlin APIs, rebuild frameworks:

```bash
./gradlew :syncforge:linkDebugFrameworkIosSimulatorArm64 \
          :sample-ios-shared:linkDebugFrameworkIosSimulatorArm64 --refresh-dependencies
```

---

## Choose an integration style

| Style | When to use | Example |
|-------|-------------|---------|
| **Kotlin facade + callbacks** | Fire-and-forget from Swift; lists and one-shot results | `IosSampleController.addTask { success, error in … }` |
| **SKIE `Flow` → `AsyncSequence`** | Long-lived UI state (status, conflicts) | `observeStatusLabel()` in SwiftUI |
| **SKIE `suspend` → `async`** | Call `SyncManager.sync()` / `enqueueChange` directly from Swift concurrency | See § Suspend functions below |
| **Hybrid (recommended)** | Production apps | Facade for mutations; SKIE Flow for observation |

The [`ios-sample`](../ios-sample/) app uses a **hybrid**: `SampleKotlinBridge` wraps `IosSampleController`; status uses SKIE Flow (`KotlinFlowInterop`); entity lists use callback listeners.

---

## Flow collection (SwiftUI)

SKIE exports Kotlin `Flow` and `StateFlow` as Swift `AsyncSequence`. Collect in a `Task` and update UI on the main actor.

### Minimal pattern

```swift
import SyncForgeSample

private var statusTask: Task<Void, Never>?

func startObserving(_ controller: IosSampleController) {
    statusTask?.cancel()
    statusTask = KotlinFlowInterop.collect(controller.observeStatusLabel()) { label in
        self.statusLabel = label
        self.isSyncing = label.localizedCaseInsensitiveContains("syncing")
    }
}

deinit {
    statusTask?.cancel()
}
```

`IosSampleController.observeStatusLabel()` maps `SyncManager.status` to human-readable labels (same strings as Android Compose).

### Direct `SyncManager` access

When you own the `SyncManager` in Kotlin and expose it to Swift:

```kotlin
// Kotlin (iosMain)
fun observeSyncStatusUiModel() =
    syncManager.status.map { it.toUiModel() }
```

```swift
// Swift — SKIE exports Flow of SyncStatusUiModel
for await model in controller.observeSyncStatusUiModel() {
    await MainActor.run {
        bannerText = model.label
        showSpinner = model.isSyncing
    }
}
```

**Cancellation:** Always cancel the collecting `Task` when the view model or view controller is torn down. Uncancelled collectors keep the Kotlin coroutine scope alive.

**Threading:** Kotlin emits on its coroutine dispatcher (often `Dispatchers.Main` in facades). `KotlinFlowInterop.collect` re-dispatches to `@MainActor` for SwiftUI safety.

---

## Callback APIs (mutations and lists)

Facade methods that take `(Boolean, String?) -> Unit` run work in a Kotlin `CoroutineScope` and call back on the main dispatcher. From Swift:

```swift
controller.addTask(title: title) { success, error in
    if KotlinInterop.bool(success) {
        self.clearField()
    } else {
        self.errorMessage = KotlinInterop.errorMessage(error) ?? "Failed to add task"
    }
}
```

### Kotlin / Native type quirks

| Kotlin | Swift | Helper |
|--------|-------|--------|
| `Boolean` | `Bool` or `KotlinBoolean` | `KotlinInterop.bool(_:)` |
| `List<T>` | `[T]` or `NSArray` | `KotlinInterop.mapTasks(_:)` etc. |
| `String?` error | `String?` | Use as-is |
| Thrown exception | `KotlinThrowable` / `NSError` | `KotlinInterop.errorMessage(_:)` |

See [`KotlinInterop.swift`](../ios-sample/SyncForgeTasks/KotlinInterop.swift) in the sample app.

---

## Suspend functions and errors

With `SuspendInterop.Enabled(true)`, Kotlin `suspend fun` becomes Swift `async` on the exported type.

```swift
import SyncForge

// Inside an async context (e.g. Task { })
let manager: SyncManager = … // from your Kotlin entry point
do {
    let result = try await manager.sync()
    // SyncResult is a Kotlin sealed class — use when/is or helper in your facade
} catch {
    let message = KotlinInterop.errorMessage(error) ?? "Sync failed"
    await MainActor.run { self.errorMessage = message }
}
```

**Recommendations:**

1. **Prefer a Kotlin facade** for complex `SyncResult` / sealed-class handling — keeps Swift thin.
2. **Use `runCatching` in Kotlin** and surface `(success, errorMessage?)` callbacks when you want to avoid `try/catch` in Swift (as `IosSampleController` does).
3. **Do not block the main thread** — always call suspend APIs from `Task` or `async` Swift code.

---

## Error handling checklist

| Scenario | Pattern |
|----------|---------|
| Validation error before network | Facade calls `onComplete(false, "Title must not be blank")` |
| `enqueueChange` / DB failure | `runCatching { … }.fold(onFailure = { onComplete(false, it.message) })` |
| Sync transport failure | `SyncStatus.Error` on `status` Flow; read label via `toUiModel()` |
| User-visible message | Prefer `error.message` from Kotlin; fall back to generic string in Swift |
| Logging | `#if DEBUG` print in Flow collector catch; avoid logging tokens |

---

## Background sync and BGTask

Register background tasks from Swift **once** at launch (non-E2E builds):

```swift
IosBackgroundSyncKt.registerIosBackgroundSyncTasks(
    taskIdentifier: "com.example.app.sync.refresh"
)
```

Match `backgroundSyncTaskIdentifier(...)` in `SyncForge.ios { }`. See [IOS_SETUP.md — Background sync](IOS_SETUP.md#background-sync-bgtaskscheduler).

---

## What SKIE does not solve

- **Experimental APIs** (`@ExperimentalSyncForgeApi`) — still require Kotlin opt-in; not all are exported to Swift.
- **Compose UI** — Android-only; use SwiftUI/UIKit on iOS.
- **Automatic memory management across boundaries** — cancel Flow tasks; avoid retaining Kotlin controllers in global singletons without lifecycle.
- **SPM binary distribution** — until `v2.0.0`; integrate via KMP frameworks today ([RELEASE.md](RELEASE.md)).

---

## Reference implementation

| File | Role |
|------|------|
| [`KotlinFlowInterop.swift`](../ios-sample/SyncForgeTasks/KotlinFlowInterop.swift) | Reusable Flow → `@MainActor` collector |
| [`KotlinInterop.swift`](../ios-sample/SyncForgeTasks/KotlinInterop.swift) | `Bool`, list, and error mapping |
| [`SampleKotlinBridge.swift`](../ios-sample/SyncForgeTasks/SampleKotlinBridge.swift) | Production bridge to `IosSampleController` |
| [`SampleViewModel.swift`](../ios-sample/SyncForgeTasks/SampleViewModel.swift) | SwiftUI view model — Flow for status, callbacks for lists |
| [`IosSampleController.kt`](../sample-ios-shared/src/iosMain/kotlin/dev/syncforge/sample/ios/IosSampleController.kt) | Kotlin facade |

---

## Related docs

- [IOS_SETUP.md](IOS_SETUP.md) — DSL, Xcode, mock server
- [sample-ios-shared/README.md](../sample-ios-shared/README.md) — framework build API table
- [ios-sample/README.md](../ios-sample/README.md) — runnable SwiftUI demo