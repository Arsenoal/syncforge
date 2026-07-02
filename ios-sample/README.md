# SyncForge Tasks — iOS Sample (SwiftUI)

SwiftUI app demonstrating `SyncForge.ios { }` via the `:sample-ios-shared` framework and `IosSampleController`.

## Prerequisites

- macOS with Xcode 15+
- JDK 17+ (for Gradle framework builds)
- iOS Simulator (arm64 recommended on Apple Silicon Macs)

## Quick start

**Terminal 1 — mock server:**

```bash
./gradlew :mock-server:run
```

**Terminal 2 — build Kotlin frameworks (optional; Xcode Run Script also does this):**

```bash
./gradlew :sample-ios-shared:linkDebugFrameworkIosSimulatorArm64
```

**Open in Xcode:**

```bash
open ios-sample/SyncForgeTasks.xcodeproj
```

Select an iOS Simulator target and press **Run** (⌘R). Default base URL: `http://localhost:8080`.

## Project layout

```
ios-sample/
├── SyncForgeTasks.xcodeproj/     Xcode project
├── SyncForgeTasks/               SwiftUI sources
│   ├── SyncForgeTasksApp.swift   @main entry point
│   ├── ContentView.swift         Root view shell
│   ├── TasksView.swift           Task list, add field, sync toolbar, status banner
│   ├── SampleViewModel.swift     Wraps IosSampleController (ObservableObject)
│   ├── TaskRowView.swift         Single task row with sync state badge
│   └── Info.plist                ATS allows local networking (mock server)
├── Configuration/
│   └── Frameworks.xcconfig       Framework search paths + linker flags
└── Scripts/
    └── build-kotlin-frameworks.sh  Run Script invoked before each Xcode build
```

## Kotlin ↔ Swift wiring

| Swift | Kotlin |
|-------|--------|
| `SampleViewModel` | `IosSampleController` |
| `TaskItem` | `dev.syncforge.sample.ios.TaskItem` |
| `IOS_SAMPLE_DEFAULT_BASE_URL` | top-level constant in `IosSampleController.kt` |

The Xcode **Build Kotlin Frameworks** phase links static frameworks:

- `SyncForge.framework` — `:syncforge`
- `SyncForgeSample.framework` — `:sample-ios-shared`

## Conflict demo (optional)

Tasks use `deferToUser()` (same as the Android `:sample` app). To trigger a conflict:

1. Add a task and tap **Sync**
2. Edit the same task on the mock server (`POST /dev/simulate-edit`)
3. Edit the task locally in the app (not yet in UI — re-add or use server edit only for title conflicts from pull)
4. Tap **Sync** — status banner shows conflict count

Full conflict resolution UI is deferred; the status label surfaces open conflicts.

## Physical device

Replace `http://localhost:8080` with your Mac's LAN IP (e.g. `http://192.168.1.10:8080`) in `SampleViewModel.init` or pass a custom URL to `IosSampleController(baseUrl:)`.

See [sample-ios-shared/README.md](../sample-ios-shared/README.md) and [docs/IOS_SETUP.md](../docs/IOS_SETUP.md).