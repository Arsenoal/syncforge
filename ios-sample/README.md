# SyncForge Sample — iOS (SwiftUI)

SwiftUI app demonstrating `SyncForge.ios { }` via the `:sample-ios-shared` framework and `IosSampleController`. Mirrors Android `:sample`: **tasks**, **notes**, and **tags** on one `SyncManager` with tab navigation.

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
│   ├── ContentView.swift         TabView: Tasks | Notes | Tags + shared status banner
│   ├── TasksView.swift           Task list and add field
│   ├── NotesView.swift           Note list, title/body fields, delete
│   ├── TagsView.swift            Tag list, add field, delete
│   ├── SampleStatusBanner.swift  Shared sync status + Sync button
│   ├── SampleViewModel.swift     Wraps IosSampleController (ObservableObject)
│   ├── TaskRowView.swift         Task row with sync state badge
│   ├── NoteRowView.swift         Note row with sync state badge
│   ├── TagRowView.swift          Tag row with sync state badge
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
| `TaskItem` / `NoteItem` / `TagItem` | Swift-friendly row DTOs from `:sample-ios-shared` |
| `IOS_SAMPLE_DEFAULT_BASE_URL` | top-level constant in `IosSampleController.kt` |

The Xcode **Build Kotlin Frameworks** phase links static frameworks:

- `SyncForge.framework` — `:syncforge`
- `SyncForgeSample.framework` — `:sample-ios-shared`

## Conflict demo (optional)

Conflict policies match Android `:sample`: tasks `deferToUser()`, notes and tags `lastWriteWins()`. To trigger a task conflict:

1. Add a task and tap **Sync**
2. Edit the same task on the mock server (`POST /dev/simulate-edit`)
3. Edit the task locally in the app (not yet in UI — re-add or use server edit only for title conflicts from pull)
4. Tap **Sync** — status banner shows conflict count

Full conflict resolution UI is deferred; the status label surfaces open conflicts.

## Manual verification (multi-entity)

With `:mock-server` running on the host:

1. Add a task on **Tasks**, a note on **Notes**, and a tag on **Tags** (no restart).
2. Tap **Sync** once — all three entity types should push in a single cycle.
3. Confirm entries on the mock server (or pull from a second client).
4. Optional conflict isolation: trigger a task conflict via server edit; add/sync a note — notes should still sync independently.

Stretch: XCTest UI smoke tests (not yet in CI).

## Physical device

Replace `http://localhost:8080` with your Mac's LAN IP (e.g. `http://192.168.1.10:8080`) in `SampleViewModel.init` or pass a custom URL to `IosSampleController(baseUrl:)`.

See [sample-ios-shared/README.md](../sample-ios-shared/README.md) and [docs/IOS_SETUP.md](../docs/IOS_SETUP.md).