# SyncForge iOS Sample (Kotlin shared)

Kotlin framework (`SyncForgeSample`) demonstrating `SyncForge.ios { }` for SwiftUI integration.

## Build the framework

On macOS with Xcode installed:

```bash
# Simulator (Apple Silicon Mac — recommended)
./gradlew :syncforge:linkDebugFrameworkIosSimulatorArm64
./gradlew :sample-ios-shared:linkDebugFrameworkIosSimulatorArm64
```

Output:

```
syncforge/build/bin/iosSimulatorArm64/debugFramework/SyncForge.framework
sample-ios-shared/build/bin/iosSimulatorArm64/debugFramework/SyncForgeSample.framework
```

`:sample-ios-shared` depends on `:syncforge` — build both, or let the Xcode Run Script build them automatically.

## SwiftUI sample app

A full Xcode project lives in [`ios-sample/`](../ios-sample/):

```bash
open ios-sample/SyncForgeTasks.xcodeproj
```

The app uses `SampleViewModel` → `IosSampleController` with a task list, add/sync controls, and status banner. See [ios-sample/README.md](../ios-sample/README.md).

## Run with mock server

**Terminal 1:**

```bash
./gradlew :mock-server:run
```

**Terminal 2:** Run the iOS app in Simulator (`⌘R` in Xcode). Base URL: `http://localhost:8080` (not `10.0.2.2` — that is Android emulator only).

## Manual Xcode integration

If you embed the frameworks in your own app instead of using `ios-sample/`:

1. Add a **Run Script** build phase (before Compile Sources):

   ```bash
   "${SRCROOT}/../ios-sample/Scripts/build-kotlin-frameworks.sh"
   ```

   Or inline:

   ```bash
   cd "${SRCROOT}/.."
   ./gradlew :syncforge:linkDebugFrameworkIosSimulatorArm64 :sample-ios-shared:linkDebugFrameworkIosSimulatorArm64
   ```

2. **Build Settings → Framework Search Paths** (Debug, Simulator):

   ```
   $(SRCROOT)/../syncforge/build/bin/iosSimulatorArm64/debugFramework
   $(SRCROOT)/../sample-ios-shared/build/bin/iosSimulatorArm64/debugFramework
   ```

3. **Other Linker Flags:** `-framework SyncForge -framework SyncForgeSample`

4. **Info.plist:** allow local HTTP (mock server):

   ```xml
   <key>NSAppTransportSecurity</key>
   <dict>
       <key>NSAllowsLocalNetworking</key>
       <true/>
   </dict>
   ```

5. Import and use:

   ```swift
   import SyncForgeSample

   let controller = IosSampleController(baseUrl: IOS_SAMPLE_DEFAULT_BASE_URL)

   controller.setStatusListener { label in
       print("Sync status: \(label)")
   }

   controller.setTasksListener { tasks in
       print("Tasks: \(tasks.count)")
   }

   controller.addTask(title: "Buy milk") { success, error in
       if success.boolValue { controller.sync { _, _ in } }
   }
   ```

Static frameworks — **Do Not Embed**; link via `OTHER_LDFLAGS`.

## API reference

| Method / type | Description |
|---------------|-------------|
| `IosSampleController(baseUrl)` | Wires `SyncForge.ios` with SQLDelight, UserDefaults cursor, NWPathMonitor, `deferToUser()` for tasks |
| `IOS_SAMPLE_DEFAULT_BASE_URL` | `http://localhost:8080` — exported for Swift |
| `TaskItem` | Swift-friendly task row (`id`, `title`, `completed`, `syncStateLabel`) |
| `addTask(title, onComplete)` | Creates a task and enqueues a sync change |
| `sync(onComplete)` | Runs full push + pull cycle |
| `setStatusListener(listener)` | Receives status label updates (callback API) |
| `observeStatusLabel()` | SKIE `Flow` → Swift `AsyncSequence` for status labels |
| `setTasksListener(listener)` | Receives task list updates |
| `currentStatusLabel()` | Synchronous status snapshot |

See [docs/IOS_SETUP.md](../docs/IOS_SETUP.md) for full iOS configuration options.