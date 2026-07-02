# iOS setup guide

Configure SyncForge on Apple platforms using `SyncForge.ios { }`.

**Requirements:** Kotlin 2.1+, Xcode 15+, iOS 14+ deployment target (Network framework).

---

## Quick start

```kotlin
import dev.syncforge.SyncForge
import dev.syncforge.ios

val syncManager = SyncForge.ios {
    baseUrl("https://api.example.com")
    registry(handlers)
}
```

### Defaults applied automatically

| Component | Implementation |
|-----------|----------------|
| Outbox + conflicts | SQLDelight — schemas/drivers in `:syncforge-persistence`, repos in `:syncforge` `syncPersistenceMain`; DB in app Documents directory |
| Pull cursor | `UserDefaults` via `SyncCursorStoreFactory.create()` |
| Network | `NWPathMonitor` via `NetworkMonitorFactory.create()` |
| Transport | `KtorSyncTransport` (Darwin engine) |
| Retry | Exponential backoff (same as Android) |
| Reconnect sync | Auto-push when network returns (via `SyncManagerImpl`) |
| Background sync | `NoOpSyncWorkScheduler` (BGTaskScheduler deferred) |

---

## DSL reference

### Required

```kotlin
baseUrl("https://api.example.com")
registry(EntityRegistry.of(taskHandler))
```

### Overrides

```kotlin
cursorStore(myStore)                    // custom SyncCursorStore
networkMonitor(myMonitor)               // custom NetworkMonitor
persistence(customPersistence)          // custom SyncForgePersistence
transport(customTransport)
scope(customScope)
conflicts { entity("tasks") { lastWriteWins() } }
customize { maxRetries = 10 }           // SyncForgeBuilder escape hatch
```

### Convenience helpers

```kotlin
cursorStoreAppGroup("group.com.myapp.sync")  // App Group UserDefaults
networkMonitorAlwaysOnline()                 // skip NWPathMonitor (tests)
```

---

## Cursor persistence

```kotlin
// Default — standard UserDefaults
SyncForge.ios { /* ... */ }

// App Group — share cursor with extensions/widgets
SyncForge.ios {
    cursorStoreAppGroup("group.com.myapp.syncforge")
    // ...
}

// Manual
SyncForge.ios {
    cursorStore(SyncCursorStoreFactory.create(suiteName = "group.com.myapp.syncforge"))
}
```

Key: `dev.syncforge.last_sync_cursor_millis` (override via `SyncCursorStoreFactory.create(key = "...")`).

---

## Network monitoring

`IosNetworkMonitor` uses `NWPathMonitor`. When connectivity is restored, `SyncManagerImpl` automatically pushes pending outbox entries (same behavior as Android `ConnectivityManager`).

```kotlin
// Default — production
SyncForge.ios { /* uses IosNetworkMonitor */ }

// Simulator / unit testing without path changes
SyncForge.ios {
    networkMonitorAlwaysOnline()
}
```

`SyncStatus.Offline` is emitted when `requireNetwork = true` and the device has no connectivity.

---

## Local development with mock server

```bash
./gradlew :mock-server:run
```

| Environment | Base URL |
|-------------|----------|
| iOS Simulator | `http://localhost:8080` |
| Physical device | Your Mac's LAN IP, e.g. `http://192.168.1.10:8080` |
| Android emulator | `http://10.0.2.2:8080` (not applicable on iOS) |

---

## Xcode integration

### Option A — Open the sample app (recommended)

```bash
./gradlew :mock-server:run          # Terminal 1
open ios-sample/SyncForgeTasks.xcodeproj
```

Press **Run** (⌘R) on an iOS Simulator. The Xcode project includes a **Build Kotlin Frameworks** Run Script that invokes Gradle before each build.

See [ios-sample/README.md](../ios-sample/README.md).

### Option B — Embed in your own app

1. Build frameworks on macOS:

```bash
./gradlew :syncforge:linkDebugFrameworkIosSimulatorArm64
./gradlew :sample-ios-shared:linkDebugFrameworkIosSimulatorArm64
```

2. Add a Run Script build phase (see [ios-sample/Scripts/build-kotlin-frameworks.sh](../ios-sample/Scripts/build-kotlin-frameworks.sh)).
3. Set **Framework Search Paths** to the Gradle `debugFramework` output directories.
4. Link with **Other Linker Flags:** `-framework SyncForge -framework SyncForgeSample` (static — do not embed).
5. Add `NSAllowsLocalNetworking` to Info.plist for local mock server HTTP.
6. Import Kotlin types in Swift: `import SyncForgeSample`.

See [sample-ios-shared/README.md](../sample-ios-shared/README.md) for manual wiring details.

### SKIE (M5)

`:syncforge` and `:sample-ios-shared` apply the [SKIE](https://skie.touchlab.co/) plugin when building frameworks. This improves Swift interop for:

- `Flow` → Swift `AsyncSequence` (e.g. `IosSampleController.observeStatusLabel()`)
- Suspend functions where exported

Rebuild frameworks after upgrading SKIE or Kotlin:

```bash
./gradlew :syncforge:linkDebugFrameworkIosSimulatorArm64 :sample-ios-shared:linkDebugFrameworkIosSimulatorArm64 --refresh-dependencies
```

---

## Sample controller API

```kotlin
val controller = IosSampleController(baseUrl = IOS_SAMPLE_DEFAULT_BASE_URL)

controller.setStatusListener { label -> /* update UI */ }
controller.setTasksListener { tasks -> /* update list */ }
controller.addTask("Buy milk") { success, error -> /* ... */ }
controller.sync { success, status -> /* ... */ }
```

SwiftUI wrapper: `ios-sample/SyncForgeTasks/SampleViewModel.swift`.

---

## Android parity

| Feature | Android | iOS |
|---------|---------|-----|
| Setup DSL | `SyncForge.android(context)` | `SyncForge.ios` |
| Outbox + conflicts | SQLDelight (`syncforge.db`) | SQLDelight |
| Legacy opt-in | `useRoomPersistence()` (deprecated) | — |
| Cursor | SharedPreferences | UserDefaults |
| Network | ConnectivityManager | NWPathMonitor |
| Background sync | WorkManager | No-op (BGTaskScheduler deferred) |

Both platforms share SQLDelight repository implementations in `syncPersistenceMain`.
Android-only types (WorkManager, Compose UI) live in `androidMain`.