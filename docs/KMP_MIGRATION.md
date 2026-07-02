# KMP migration plan

**Status:** Phase 6 persistence cutover complete (M1–M5 + M4 Android default); M6 distribution next  
**Goal:** Kotlin Multiplatform support with iOS and desktop targets **without breaking Android app integration**.

---

## Principles

1. **Android stays the reference platform** until iOS reaches parity.
2. **Interfaces in `commonMain`, implementations per platform** — no Room/SQLDelight types leak into shared code.
3. **Incremental migration** — each PR compiles on Android + JVM; iOS targets added progressively.
4. **SQLDelight is the internal persistence backend on all platforms** since 0.6.0. Legacy Room internals remain only for `useRoomPersistence()` and migration.
5. **Consumer app entities stay in Room** (Android) — only SyncForge's internal outbox/conflict DB moved to SQLDelight.

---

## Current state (v0.6.0 / M5 complete)

### Source set layout

```
syncforge/src/
├── commonMain/           ← ~85% of sync logic (engine, conflicts, models, debug API)
├── syncPersistenceMain/  ← SQLDelight OutboxRepository + ConflictStore impls (M4)
├── commonTest/
├── androidMain/          ← SQLDelight default, legacy Room (internal), WorkManager, Compose UI
├── androidUnitTest/
├── jvmMain/              ← SyncForge.desktop { }, FileSyncCursorStore, OkHttp transport
├── jvmTest/              ← SQLDelight + FileSyncCursorStore tests (JDBC in-memory driver)
├── iosMain/              ← Darwin Ktor, SyncForge.ios { } DSL (SQLDelight default)
└── macosMain/            ← SyncForge.macos { } (delegates to ios DSL; shared Darwin services)
```

### Module dependency graph (M4)

```
:syncforge-persistence   schemas · drivers · SyncForgePersistence.create()
        ↑
:syncforge syncPersistenceMain   SqlDelightOutboxRepository · SqlDelightConflictStore
        ↑
androidMain / iosMain / jvmMain / macosMain
```

`:syncforge` **commonMain** does not depend on `:syncforge-persistence`. The circular
dependency (`:syncforge` ↔ `:syncforge-persistence`) is resolved by keeping repository
implementations in `:syncforge` and persistence-only code in `:syncforge-persistence`.

### Platform-agnostic (already in `commonMain`)

| Package | Types |
|---------|-------|
| `dev.syncforge.model` | `Change`, `OutboxEntry`, `SyncStatus`, `SyncResult`, `SyncState` |
| `dev.syncforge.entity` | `SyncedEntity`, `EntitySyncHandler`, `TypedEntitySyncHandler`, `EntityRegistry` |
| `dev.syncforge.outbox` | `OutboxRepository` (interface), `InMemoryOutboxRepository` |
| `dev.syncforge.conflict` | All strategies, `ConflictPolicy`, `ConflictStore` (interface), `ConflictPullApplier` |
| `dev.syncforge.sync` | `SyncManager`, `SyncEngine`, `SyncConfig`, retry, cursor interface |
| `dev.syncforge.network` | `SyncTransport` (interface), `NetworkMonitor` (interface), REST DTOs |
| `dev.syncforge.network` | `KtorSyncTransport`, `SyncAuthProvider` *(moved in 0.4.x KMP prep)* |
| `dev.syncforge.debug` | `SyncDebug`, `SyncHealth`, `SyncEvent` |
| `dev.syncforge.compose` | `SyncStatusUiModel`, `toUiModel()` (commonMain); `collectSyncStatusUiModel()` (androidMain) |
| `dev.syncforge` | `SyncForge`, `SyncForgeBuilder` |

### Android-only (`androidMain`)

| Package | Types | KMP replacement |
|---------|-------|-----------------|
| `dev.syncforge.outbox` | Legacy Room types (`internal`, deprecated opt-in only) | `SqlDelightOutboxRepository` (default) |
| `dev.syncforge.conflict` | Legacy Room types (`internal`, deprecated opt-in only) | `SqlDelightConflictStore` (default) |
| `dev.syncforge.sync` | `SharedPreferencesSyncCursorStore` | Multiplatform DataStore or SQLDelight |
| `dev.syncforge.network` | `AndroidNetworkMonitor`, `NetworkMonitorFactory` | `expect/actual` network reachability |
| `dev.syncforge.work` | `AndroidSyncWorkScheduler`, `SyncWorker` | `IosBackgroundSyncWorkScheduler` (BGTaskScheduler) ✅ |
| `dev.syncforge` | `SyncForge.android`, `SyncForgeAndroid` | Stays Android-only |
| `dev.syncforge.compose` | `SyncDebugPanel`, `SyncConflictUi` | SwiftUI (Phase 6b) or CMP (later) |

### `expect`/`actual` today

| Declaration | `commonMain` | `androidMain` | `iosMain` | `jvmMain` | `macosMain` |
|-------------|--------------|---------------|-----------|-----------|-------------|
| `currentTimeMillis()` | `expect` | `actual` | `actual` | `actual` | `actual` (via ios) |
| `createPlatformHttpClient()` | `expect` | OkHttp engine | Darwin engine | OkHttp engine | Darwin engine (via ios) |
| `createDefaultSyncForgePersistence()` | `expect` | Context required | Documents dir SQLite | Temp file SQLite | App Support SQLite |
| `NetworkMonitorFactory.create()` | per-platform factories | `Context` | `NWPathMonitor` | `AlwaysOnlineNetworkMonitor` | `NWPathMonitor` (via ios) |
| `SyncCursorStoreFactory.create()` | per-platform factories | `Context` / SharedPreferences | `UserDefaults` | `FileSyncCursorStore` (`~/.syncforge/`) | `UserDefaults` (via ios) |

---

## Target architecture (v0.5.0)

```
┌──────────────────────────────────────────────────────────────────┐
│                           commonMain                              │
│  SyncEngine · ConflictPolicy · SyncManager · KtorTransport       │
│  OutboxRepository ◄──interface──► ConflictStore ◄──interface     │
│  SyncCursorStore ◄──interface──► NetworkMonitor ◄──interface     │
│  SyncWorkScheduler ◄──interface──►                                │
└──────┬──────────────┬──────────────┬──────────────┬──────────────┘
       │              │              │              │
┌──────▼──────┐ ┌─────▼─────┐ ┌──────▼──────┐ ┌─────▼─────┐
│ androidMain │ │  iosMain  │ │   jvmMain   │ │ macosMain │
│ SQLDelight  │ │ SQLDelight│ │ SQLDelight  │ │ (→ ios)   │
│ WorkManager │ │ UserDef.  │ │ File cursor │ │ SQLDelight│
│ Compose UI  │ │ NWPathMon.│ │ AlwaysOnline│ │ NWPathMon.│
└─────────────┘ └───────────┘ └─────────────┘ └───────────┘
```

### Persistence: Room → SQLDelight

**Why SQLDelight:** First-class KMP support, typed queries, migration story, Flow observation.

| Legacy Room table (pre-0.6.0) | SQLDelight (default since 0.6.0) | Notes |
|-------------------------------|----------------------------------|-------|
| `syncforge_outbox` | `OutboxEntry.sq` | Same columns as `OutboxEntryEntity` |
| `syncforge_conflicts` | `ConflictEntry.sq` | Same columns as `ConflictEntryEntity` |

**Migration steps:**

1. **0.4.x** — Define `OutboxRepository` / `ConflictStore` contracts; add `InMemory*` impls for iOS bootstrap.
2. **0.5.0-alpha** ✅ — `:syncforge-persistence` module with SQLDelight schemas; repository impls in `:syncforge` `syncPersistenceMain`.
3. **0.5.0-beta** ✅ — `SyncForge.ios { }` wires SQLDelight by default.
4. **0.5.0-rc** ✅ — Android SQLDelight opt-in (superseded by 0.6.0 default; `useSqlDelightPersistence()` removed at 1.0).
5. **0.6.0** ✅ — SQLDelight is Android default; legacy Room internals are `internal` + deprecated opt-in; `RoomToSqlDelightMigrator` runs automatically.

**Consumer app entities stay in Room** (or GRDB on iOS) — SyncForge only owns its internal outbox/conflict DB.

### Cursor store

| Platform | v0.4.x (now) | v0.5.0 target |
|----------|--------------|---------------|
| Android | `SharedPreferences` | DataStore Preferences (multiplatform) |
| iOS | `UserDefaultsSyncCursorStore` ✅ | DataStore optional later |
| JVM desktop | `FileSyncCursorStore` (`~/.syncforge/`) ✅ | DataStore optional later |
| macOS native | `UserDefaultsSyncCursorStore` (via ios) ✅ | DataStore optional later |
| Tests | `InMemorySyncCursorStore` | unchanged |

```kotlin
// Future commonMain contract
interface SyncCursorStoreFactory {
    fun create(): SyncCursorStore
}
// expect fun defaultCursorStoreFactory(): SyncCursorStoreFactory
```

### Network monitor

```kotlin
// commonMain — already exists
interface NetworkMonitor {
    val isOnline: Boolean
    fun observeOnline(): Flow<Boolean>
}

// androidMain — ConnectivityManager (done)
// iosMain — NWPathMonitor wrapper (0.5.0)
// fallback — AlwaysOnlineNetworkMonitor (done)
```

### Background sync scheduler

```kotlin
// commonMain — SyncWorkScheduler interface exists
interface SyncWorkScheduler {
    fun schedulePeriodicSync(interval: Duration)
    fun scheduleRetry(delay: Duration)
    fun cancel()
}

// androidMain — WorkManager (done)
// iosMain — IosBackgroundSyncWorkScheduler (BGAppRefreshTask) ✅
```

### HTTP transport

`KtorSyncTransport` is now in `commonMain`. Platform engines via `expect/actual`:

| Platform | Engine |
|----------|--------|
| Android | `ktor-client-okhttp` |
| iOS | `ktor-client-darwin` |
| JVM (tests) | `ktor-client-okhttp` or `MockEngine` |

---

## `expect`/`actual` roadmap

| Item | Phase | Priority |
|------|-------|----------|
| `currentTimeMillis()` | ✅ Done | — |
| `createPlatformHttpClient()` | ✅ Done | — |
| `createNetworkMonitor()` | ✅ Done (per-platform factories) | — |
| `createCursorStore()` | ✅ Done (per-platform factories) | — |
| `createOutboxRepository()` | ✅ Done (extensions) | — |
| `createConflictStore()` | ✅ Done (extensions) | — |
| `createWorkScheduler()` | 0.5.0 | Medium |
| `dispatchersMain()` | 0.5.0 | Low (use Default for now) |

---

## Platform entry points (v0.5.0)

### iOS / macOS

`SyncForge.ios { }` and `SyncForge.macos { }` provide a working sync manager with:

| Component | Implementation |
|-----------|----------------|
| Outbox | SQLDelight (`createDefaultSyncForgePersistence()`) |
| Conflicts | SQLDelight |
| Cursor | `UserDefaultsSyncCursorStore` |
| Network | `IosNetworkMonitor` (override with `networkMonitorAlwaysOnline()` for tests) |
| Transport | `KtorSyncTransport` (Darwin) |
| Background sync | `IosBackgroundSyncWorkScheduler` (BGAppRefreshTask) |

```kotlin
val syncManager = SyncForge.ios {
    baseUrl("https://api.example.com")
    registry(handlers)
    conflicts { entity("tasks") { lastWriteWins() } }
}
```

Outbox and conflict data persist across process death in the app Documents directory.

### JVM desktop

`SyncForge.desktop { }` wires SQLDelight (JDBC SQLite), `FileSyncCursorStore` at `~/.syncforge/`,
`AlwaysOnlineNetworkMonitor`, and OkHttp transport. See [DESKTOP_SETUP.md](DESKTOP_SETUP.md).

---

## Module structure (future)

```
:syncforge                    ← public API + SQLDelight repository impls (syncPersistenceMain)
:syncforge-annotations        ← KSP annotations (unchanged)
:syncforge-ksp                ← handler codegen (unchanged; SKIE on framework modules for Swift)
:syncforge-persistence        ← SQLDelight schemas + platform drivers only (no :syncforge dep)
```

Keeping schemas/drivers in a separate module avoids pulling SQLDelight into consumers who bring their own storage (advanced use). Repository wiring stays in `:syncforge` so persistence does not depend on sync contracts.

---

## Milestone schedule

### M1 — KMP groundwork (v0.4.1) ✅

- [x] Add `iosMain` source set + iOS Gradle targets
- [x] Move `KtorSyncTransport` to `commonMain`
- [x] `expect/actual` HTTP client factory
- [x] `SyncForge.ios { }` bootstrap DSL
- [x] `InMemoryConflictStore` promoted to `commonMain`
- [x] This migration document

### M2 — SQLDelight module (v0.5.0-alpha) ✅

- [x] `:syncforge-persistence` with outbox + conflict schemas
- [x] `SqlDelightOutboxRepository` implements `OutboxRepository` (in `:syncforge` `syncPersistenceMain`)
- [x] `SqlDelightConflictStore` implements `ConflictStore` (in `:syncforge` `syncPersistenceMain`)
- [x] iOS `SyncForge.ios` wires SQLDelight by default
- [x] JVM tests against SQLDelight in-memory driver (`:syncforge:jvmTest`)

### M3 — iOS platform services (v0.5.0-beta) ✅

- [x] `IosNetworkMonitor` (NWPathMonitor) + `NetworkMonitorFactory.create()`
- [x] Persisted cursor (`UserDefaultsSyncCursorStore` + `SyncCursorStoreFactory.create(suiteName?)`)
- [x] `SyncForge.ios` DSL overrides (`cursorStore`, `networkMonitor`, convenience helpers)
- [x] `:sample-ios-shared` framework + `IosSampleController` (Swift callback API)
- [x] [IOS_SETUP.md](IOS_SETUP.md) + sample README
- [x] Full Xcode SwiftUI project (`ios-sample/SyncForgeTasks.xcodeproj`)
- [x] Background sync hook (`IosBackgroundSync` + BGAppRefreshTask)

### M4 — Android SQLDelight parity (v0.5.0-rc) ✅

- [x] `createSyncForgePersistence(context)` + `SyncForgePersistenceFactory.create(context)` (Android)
- [x] `SyncForge.android { databaseName() / persistence() }` DSL
- [x] Circular dependency resolved (`:syncforge-persistence` → schemas/drivers only)
- [x] `syncPersistenceMain` source set for SQLDelight repository implementations
- [x] Migration guide in [ANDROID_SETUP.md](ANDROID_SETUP.md)
- [x] Automatic Room → SQLDelight data migrator (`RoomToSqlDelightMigrator`)
- [x] Legacy Room internals marked `internal`; `useRoomPersistence()` deprecated
- [x] SQLDelight as default in `SyncForge.android` (0.6.0)

### M5 — Desktop + polish (v0.5.x) ✅

- [x] JVM desktop target (`jvm` / `jvmMain`) + `SyncForge.desktop { }` DSL
- [x] `FileSyncCursorStore` for persisted desktop pull cursor
- [x] `macosArm64` / `macosX64` targets + `SyncForge.macos { }` entry point
- [x] SKIE plugin on `:syncforge` and `:sample-ios-shared` frameworks
- [x] SKIE Flow/suspend config for `dev.syncforge` and `IosSampleController`
- [x] [DESKTOP_SETUP.md](DESKTOP_SETUP.md)
- [ ] DataStore Preferences multiplatform cursor (deferred to M6)
- [ ] Compose Multiplatform debug panel (optional)
- [x] BGTaskScheduler iOS background sync (`IosBackgroundSyncWorkScheduler`)

### M6 — Distribution (v1.0.0)

- [ ] Maven Central with per-target artifacts
- [ ] Documented iOS CocoaPods / SPM integration

---

## What NOT to change yet

- `SyncForge.android { }` signature (SQLDelight default is settled in 0.6.0)
- KSP processor output format
- Public `SyncManager` API
- `:sample` Android app (reference implementation; iOS uses `ios-sample/` + `:sample-ios-shared`)

---

## Verification checklist (each PR)

```bash
# Must pass on every KMP prep PR
./gradlew :syncforge:compileDebugKotlinAndroid
./gradlew :syncforge:compileKotlinJvm
./gradlew :syncforge:jvmTest
./gradlew :sample:compileDebugKotlin

# Apple targets (requires macOS + Xcode)
./gradlew :syncforge:compileKotlinIosSimulatorArm64
./gradlew :syncforge:compileKotlinMacosArm64
```

---

## References

- [ROADMAP.md](ROADMAP.md) — Phase 6 overview
- [MODULES.md](MODULES.md) — current package reference
- [SQLDelight KMP docs](https://cashapp.github.io/sqldelight/2.0.2/multiplatform/)
- [Ktor client engines](https://ktor.io/docs/client-engines.html)