# SyncForge module reference

This document describes every package in the `:syncforge` library module: what it does,
which types are public, and how the pieces fit together. For current limitations and
future work, see [ROADMAP.md](ROADMAP.md). For the HTTP contract, see [REST_API.md](REST_API.md).

---

## API stability (pre-1.0)

SyncForge uses three stability levels. At **1.0**, everything currently marked **Experimental**
is expected to graduate to **Stable** (or be removed) under semver.

| Level | Marker | Meaning |
|-------|--------|---------|
| **Stable** | *(no annotation)* | Supported public API; binary-compatible guarantees from 1.0 onward. |
| **Experimental** | `@ExperimentalSyncForgeApi` | Shipped for early adopters but may change in minor releases before 1.0. Requires explicit opt-in. |
| **Internal** | `internal` modifier | Implementation detail — not part of the public contract. Do not reference from app code. |

### Opting in to experimental APIs

Per call site:

```kotlin
@OptIn(ExperimentalSyncForgeApi::class)
fun setupSync() { /* ... */ }
```

Or module-wide in `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets.all {
        languageSettings.optIn("dev.syncforge.api.ExperimentalSyncForgeApi")
    }
}
```

The annotation lives in `dev.syncforge.api.ExperimentalSyncForgeApi` (`:syncforge-annotations`, re-exported by `:syncforge`).

### Stability by area

| Area | Stability | Notes |
|------|-----------|-------|
| `SyncForge.android { }`, `SyncForgeAndroid` | **Stable** | Primary Android entry point; SQLDelight outbox by default (0.6.0). |
| `SyncForge.ios { }`, `SyncForge.desktop { }`, `SyncForge.macos { }` | **Experimental** | Newer KMP platform DSLs; less production mileage than Android. |
| `SyncForge.create()`, `createWithRetry()`, `builder { }`, `SyncForgeBuilder` | **Experimental** | Low-level factory for custom wiring; parameter surface still evolving. |
| `SyncManager` — `status`, `conflicts`, `sync`/`push`/`pull`, `enqueueChange`, `resolveConflict`, `findOpenConflict`, scheduling | **Stable** | Core sync contract. |
| `SyncManager.debug`, `SyncManager.conflictHistory` | **Experimental** | Debug/QA observability; shape may change. |
| `ConflictPolicy`, `ConflictStrategies`, `ConflictChoice`, `resolveConflict` | **Stable** | Conflict-resolution API. |
| `SyncDebug`, `SyncHealth`, `SyncEvent` | **Experimental** | Developer observability. |
| `SyncDebugLauncher`, `SyncDebugPanel` | **Experimental** | Debug Compose UI (Android). |
| `SyncStatusUiModel`, `collectSyncStatusUiModel()`, conflict Compose UI | **Stable** | Production UI helpers on Android. |
| `useSqlDelightPersistence()`, `persistence(SyncForgePersistence)`, `outboxRepository()` / `conflictStore()` extensions | **Experimental** | SQLDelight wiring; default on Android since 0.6.0. |
| `useRoomPersistence()` | **Deprecated** | Legacy Room backend. |
| `SyncForgePersistenceFactory`, `createSyncForgePersistence`, `createDefaultSyncForgePersistence` (`:syncforge-persistence`) | **Experimental** | Annotated with `@ExperimentalSyncForgeApi`; requires opt-in for direct factory use. |
| `SyncEngine`, `ConflictPullApplier`, `SyncManagerImpl`, `SqlDelightOutboxRepository`, platform monitors/cursor impls | **Internal** | Orchestration and storage implementations. |

### What we hide on purpose

Types that were public but are now `internal` (or always were) include:
`SyncManagerImpl`, `SqlDelightOutboxRepository`, `SqlDelightConflictStore`, `MergeStrategy`,
`FileSyncCursorStore`, `AndroidNetworkMonitor`, `IosNetworkMonitor`, `AndroidSyncWorkScheduler`,
legacy Room internals (`RoomOutboxRepository`, `RoomConflictStore`, `SyncForgeDatabaseFactory`, etc.),
and DSL implementation classes (`AndroidSyncForgeDsl`, `IosSyncForgeDsl`, `DesktopSyncForgeDsl`).

Use the platform DSLs or `SyncManager` instead of constructing these types directly.

---

## How the library is organised

SyncForge ships as a **single Gradle module** (`:syncforge`) split into Kotlin Multiplatform
source sets:

```
syncforge/
└── src/
    ├── commonMain/           ← shared models, interfaces, sync orchestration, REST DTOs
    ├── syncPersistenceMain/  ← SQLDelight OutboxRepository + ConflictStore implementations
    ├── commonTest/           ← platform-agnostic unit tests
    ├── androidMain/          ← SQLDelight default, legacy Room (internal), WorkManager, Compose UI
    ├── androidUnitTest/      ← Android integration tests
    ├── iosMain/              ← SyncForge.ios DSL, Darwin Ktor, iOS network/cursor
    ├── macosMain/            ← SyncForge.macos DSL (delegates to ios; shared Darwin services)
    ├── jvmMain/              ← SyncForge.desktop DSL, FileSyncCursorStore, OkHttp transport
    └── jvmTest/              ← SQLDelight + FileSyncCursorStore tests (JDBC in-memory driver)
```

Companion modules:

| Module | Role |
|--------|------|
| `:syncforge-annotations` | `@SyncForgeEntity`, `@SyncForgeDao`, `@ExperimentalSyncForgeApi` |
| `:syncforge-ksp` | KSP processor — generates handlers + `SyncForgeHandlers` registry |
| `:syncforge-persistence` | SQLDelight schemas + platform drivers (`SyncForgePersistence.create()`) |
| `:mock-server` | JVM Ktor server implementing [REST_API.md](REST_API.md) |
| `:sample` | Android Compose demo app |
| `:sample-ios-shared` | iOS sample framework (`IosSampleController`; SKIE-enabled) |
| `ios-sample/` | SwiftUI Xcode app wired to `:sample-ios-shared` |

There is no separate published artifact per package. The table below maps **logical
packages** (folders under `dev.syncforge`) to their responsibility.

| Package | Source set | Purpose |
|---------|------------|---------|
| `dev.syncforge` | commonMain + android/ios/jvm/macos | Library entry point and platform DSLs |
| `dev.syncforge.model` | commonMain | Data types for changes, status, and results |
| `dev.syncforge.entity` | commonMain | Contract for sync-aware Room entities |
| `dev.syncforge.outbox` | commonMain + androidMain | Pending-change queue |
| `dev.syncforge.network` | commonMain + androidMain | Pluggable backend communication |
| `dev.syncforge.network.api` | commonMain | REST DTOs shared with `:mock-server` |
| `dev.syncforge.conflict` | commonMain + androidMain | Conflict detection, strategies, and persistence |
| `dev.syncforge.sync` | commonMain + androidMain | Sync orchestration, cursor, configuration |
| `dev.syncforge.debug` | commonMain | Developer observability API |
| `dev.syncforge.compose` | commonMain + androidMain | Compose helpers for sync status, conflicts, debug |
| `dev.syncforge.work` | androidMain + iosMain | Background sync (`WorkManager` / `BGTaskScheduler`) |
| `dev.syncforge.persistence` | syncPersistenceMain + `:syncforge-persistence` | SQLDelight outbox + conflict storage (KMP) |

---

## `dev.syncforge` — Entry point

**What it is:** Factory methods and builders to create a configured sync manager.

| Type | Stability | Role |
|------|-----------|------|
| `SyncForge.android(context) { }` | Stable | Android DSL — SQLDelight outbox by default (0.6.0); automatic Room migration |
| `SyncForgeAndroid` | Stable | `workManagerConfiguration { syncManager }` helper for `Configuration.Provider` |
| `SyncForge.ios { }` | Experimental | iOS DSL — SQLDelight outbox + conflicts, UserDefaults cursor, NWPathMonitor |
| `SyncForge.desktop { }` | Experimental | JVM desktop DSL — SQLDelight, file cursor, OkHttp transport |
| `SyncForge.macos { }` | Experimental | Native macOS DSL — same defaults as iOS (`macosArm64` / `macosX64`) |
| `SyncForge.create()` / `createWithRetry()` / `builder { }` | Experimental | Low-level factory and fluent builder |
| `SyncForgeBuilder` | Experimental | Auto-derives `entityTypes` from handlers |

### Recommended — Android DSL

```kotlin
import dev.syncforge.SyncForge
import dev.syncforge.SyncForgeAndroid
import dev.syncforge.android

class MyApplication : Application(), Configuration.Provider {

    lateinit var syncManager: SyncManager

    override val workManagerConfiguration: Configuration
        get() = SyncForgeAndroid.workManagerConfiguration { syncManager }

    override fun onCreate() {
        super.onCreate()
        val taskDao = TaskDatabase.create(this).taskDao()

        syncManager = SyncForge.android(this) {
            baseUrl("https://api.example.com")
            registry(SyncForgeHandlers.registry(taskDao))
            conflicts {
                entity("tasks") { deferToUser() }
            }
            schedulePeriodicSyncOnStart()
        }
    }
}
```

SQLDelight is the default outbox backend since 0.6.0. Upgrading from pre-0.6.0 Room storage
is automatic — see [ANDROID_SETUP.md](ANDROID_SETUP.md). Legacy Room: `useRoomPersistence()`.

KSP generates `SyncForgeHandlers` in your app module:

```kotlin
// Generated by :syncforge-ksp
object SyncForgeHandlers {
    fun taskEntity(dao: TaskDao): TaskEntitySyncHandler = ...
    fun registry(taskDao: TaskDao): EntityRegistry = EntityRegistry.of(taskEntity(taskDao))
}
```

### Advanced — low-level factory

`SyncForge.create()` and `createWithRetry()` remain available for custom wiring:

| Parameter | Required | Description |
|-----------|----------|-------------|
| `config` | Yes | `SyncConfig` — entity types, batch sizes, retry limits |
| `outbox` | Yes | `OutboxRepository` — where pending changes are stored |
| `transport` | Yes | `SyncTransport` — how to talk to your backend |
| `registry` | Yes | `EntityRegistry` — maps entity types to handlers |
| `scope` | Yes | `CoroutineScope` — internal status observation |
| `cursorStore` | No | Pull cursor persistence; defaults to `InMemorySyncCursorStore` |
| `conflictPolicy` | No | Per-entity conflict strategies; defaults to last-write-wins |
| `conflictStore` | No | Conflict persistence; defaults to `NoOpConflictStore` |

```kotlin
val persistence = SyncForgePersistenceFactory.create(context)
val syncManager = SyncForge.createWithRetry(
    config = SyncConfig(entityTypes = setOf("tasks")),
    outbox = persistence.outboxRepository(),
    transport = KtorSyncTransport("https://api.example.com"),
    registry = EntityRegistry.of(taskHandler),
    scope = appScope,
    cursorStore = SyncCursorStoreFactory.create(context),
    conflictStore = persistence.conflictStore(),
)
```

---

## `dev.syncforge.model` — Core data types

**What it is:** Plain Kotlin types shared across outbox, network, sync, and UI layers.
No Android or Room dependencies.

### `SyncState`

Per-row sync state stored on each `SyncedEntity` in Room.

| Value | Meaning |
|-------|---------|
| `SYNCED` | Matches the last known server state |
| `PENDING` | Local change queued, not yet acknowledged |
| `CONFLICT` | Local and server versions diverged |
| `FAILED` | Push failed after retries |

### `ChangeType`

| Value | Meaning |
|-------|---------|
| `CREATE` | New entity created locally |
| `UPDATE` | Existing entity modified locally |
| `DELETE` | Entity deleted locally (tombstone) |

### `Change<T>`

A single local mutation before it enters the outbox.

| Field | Description |
|-------|-------------|
| `entityType` | String identifier matching `SyncConfig.entityTypes` (e.g. `"tasks"`) |
| `entityId` | Primary key of the affected row |
| `type` | `ChangeType` |
| `payload` | The entity for CREATE/UPDATE; `null` for DELETE |
| `localVersion` | Monotonic per-row version |
| `updatedAtMillis` | Timestamp of the local edit |

Factory helpers: `Change.create()`, `Change.update()`, `Change.delete()`.

### `OutboxEntry`

The persisted form of a `Change` in the outbox. Payload is stored as JSON (`payloadJson`)
so the outbox stays entity-agnostic. Includes `rollbackSnapshotJson` for push rollback.

### `SyncStatus`

Observable sync lifecycle exposed via `SyncManager.status` (`StateFlow`).

| Variant | When |
|---------|------|
| `Idle` | No sync running; outbox empty and no recent sync |
| `Syncing(phase)` | Push, pull, or full cycle in progress |
| `Pending(outboxCount, permanentlyFailedCount, conflictCount)` | Outbox entries and/or open conflicts |
| `Offline(outboxCount)` | Queued changes but no network |
| `LastSynced(timestamp)` | Last successful pull cursor timestamp |
| `Error(message)` | User-visible or retryable error |

### `SyncResult` / `SyncError`

Outcome of a `sync()`, `push()`, or `pull()` call.

| `SyncResult` | Meaning |
|--------------|---------|
| `Success` | Completed; includes counts and optional `syncCursorMillis` |
| `Partial` | Some work done, but errors occurred |
| `Failure` | Operation failed entirely |

`SyncError.Code`: `NETWORK`, `AUTH`, `CONFLICT`, `VALIDATION`, `SERVER`, `UNKNOWN`.

---

## `dev.syncforge.entity` — Sync-aware entity contract

**What it is:** The interface your Room `@Entity` data classes implement so SyncForge can
read and write sync metadata.

### `SyncedEntity`

| Property | Description |
|----------|-------------|
| `id` | Primary key (String) |
| `localVersion` | Increments on every local edit |
| `updatedAtMillis` | Epoch millis of last local mutation |
| `syncState` | Current `SyncState` |

**Room example with KSP:**

```kotlin
@SyncForgeEntity(entityType = "tasks")
@Entity(tableName = "tasks")
@Serializable
data class TaskEntity(
    @PrimaryKey override val id: String,
    val title: String,
    override val localVersion: Long = 0,
    override val updatedAtMillis: Long = System.currentTimeMillis(),
    override val syncState: SyncState = SyncState.SYNCED,
) : SyncedEntity

@SyncForgeDao(entityClass = "com.example.TaskEntity")
@Dao
interface TaskDao { /* findById, insert, update, deleteById */ }
```

KSP generates `TaskEntitySyncHandler` and aggregates handlers in `SyncForgeHandlers`.

### `RemoteMetadata`

Server-side metadata attached to pulled deltas: `serverVersion`, `updatedAtMillis`,
`isDeleted`.

### `EntitySyncHandler` / `TypedEntitySyncHandler`

| Type | Role |
|------|------|
| `EntitySyncHandler` | Interface bridging SyncForge to your Room DAOs |
| `TypedEntitySyncHandler<T>` | Abstract base — implement DAO + JSON methods |
| `EntityRegistry` | Maps `"tasks"` → handler; `EntityRegistry.of(...)` convenience factory |

**Handler responsibilities:**

| Method | When it runs |
|--------|--------------|
| `captureSnapshot` | Before optimistic write — saved in outbox for rollback |
| `applyOptimistic` | Immediately on `enqueueChange()` |
| `rollbackEntry` | On push failure or server rejection |
| `onPushAcknowledged` | After server accepts the change |
| `applyPullDelta` | During `pull()` — delegates to `ConflictPullApplier` |
| `serializeChange` | Converts CREATE/UPDATE payload to JSON for the outbox |

---

## `dev.syncforge.outbox` — Pending changes queue

**What it is:** The outbox pattern — every local mutation is recorded, then pushed to the
server in batches.

### `OutboxRepository` (interface, commonMain)

| Method | Description |
|--------|-------------|
| `observePendingCount()` | `Flow<Int>` — drives `SyncStatus.Pending` |
| `observePending()` | `Flow<List<OutboxEntry>>` — pending entries only |
| `observeAll()` | `Flow<List<OutboxEntry>>` — all rows including failed (debug panels) |
| `enqueue(change)` | Add a `Change` to the outbox |
| `countPending()` | Current pending count |
| `peek(limit)` | Read next batch without removing |
| `markAcknowledged(ids)` | Remove entries the server accepted |
| `markFailed(id, error)` | Record a push failure and increment retry count |
| `clear()` | Remove all entries (testing / logout) |

### `InMemoryOutboxRepository` (commonMain)

Thread-safe in-memory implementation for **unit tests and prototyping**.

### `SqlDelightOutboxRepository` (syncPersistenceMain) — default on all platforms

Multiplatform SQLDelight implementation of `OutboxRepository`. Wired via extension:

```kotlin
val persistence = SyncForgePersistenceFactory.create(context)  // Android
val outbox = persistence.outboxRepository(maxRetries = 5)
```

Default on all platforms (`SyncForge.android { }`, `SyncForge.ios { }`, etc.).
Database file: `syncforge.db` (separate from your app's Room database).

### Legacy Room internals (androidMain, `internal`)

Pre-0.6.0 Room outbox/conflict storage. Not part of the public API — only used by
deprecated `useRoomPersistence()` and `RoomToSqlDelightMigrator`. Database file: `syncforge_outbox.db`.

---

## `dev.syncforge.network` — Backend communication

**What it is:** A pluggable boundary between SyncForge and your API.

### `SyncTransport` (interface)

| Method | Description |
|--------|-------------|
| `push(entries)` | Send a batch of `OutboxEntry` to the server |
| `pull(sinceTimestamp, entityTypes, pageSize, pageCursor)` | Fetch server changes since a cursor |

### Supporting types

| Type | Description |
|------|-------------|
| `PushResult` | `acknowledgedIds` + optional `rejected` list |
| `PushRejection` | A single rejected outbox entry with `SyncError` |
| `PullResult` | `deltas`, `serverTimestampMillis`, `hasMore`, `nextPageCursor` |
| `RemoteDelta` | One server change: type, id, JSON payload, version, timestamp, tombstone flag |

### `NoOpSyncTransport`

Acknowledges all pushes and returns empty pulls. Useful for tests and offline-only mode.

### `KtorSyncTransport` (commonMain)

Reference HTTP transport. Platform engines via `expect/actual` (`OkHttp` on Android/JVM,
`Darwin` on iOS). See [REST_API.md](REST_API.md) for endpoint details.

```kotlin
KtorSyncTransport(
    baseUrl = "https://api.example.com",
    authTokenProvider = { tokenStore.accessToken },
)
```

### `RefreshingSyncAuthProvider` (commonMain)

Bearer auth with a suspend refresh hook. `KtorSyncTransport` calls `refreshAccessToken()` on HTTP **401** and retries the request once (not on 403).

```kotlin
auth(
    SyncAuthProvider.refreshing(
        accessTokenProvider = { tokenStore.accessToken },
        refresh = {
            tokenStore.update(oauth.refresh(tokenStore.refreshToken))
            tokenStore.accessToken
        },
    ),
)
```

See [RECIPES.md → Token refresh on 401](RECIPES.md#token-refresh-on-401).

### `dev.syncforge.network.api` (commonMain)

Serializable DTOs (`PushRequest`, `PushResponse`, `PullResponse`, etc.) shared with
`:mock-server`. Mapping helpers convert between DTOs and transport-level types.

---

## `dev.syncforge.conflict` — Conflict resolution

**What it is:** When a pulled server version conflicts with a local row, `ConflictPolicy`
selects a strategy per entity type. Applied automatically during pull via `ConflictPullApplier`.

### `ConflictPolicy` + `conflicts { }` builder

```kotlin
conflicts {
    default(ConflictStrategies.lastWriteWins())  // optional — this is the default
    entity("tasks") {
        merge<TaskEntity> { local, remote ->
            local.copy(title = preferRemote(local.title, remote.title))
        }
    }
    entity("notes") { deferToUser() }
}
```

### `ConflictStrategies`

| Strategy | Behaviour |
|----------|-----------|
| `lastWriteWins()` | Compare `updatedAtMillis`; newer wins (global default) |
| `alwaysLocal()` | Keep local row; mark `PENDING` for re-push |
| `alwaysRemote()` | Accept server version |
| `deferToUser()` | Persist conflict in SQLDelight; surface via `SyncManager.conflicts` |
| `merge { }` | Custom field-level merge via `MergeScope` helpers |

### `MergeScope` helpers

`preferNewer()`, `preferLocal()`, `preferRemote()` — pick field values during custom merges.

### Conflict persistence

| Type | Role |
|------|------|
| `ConflictStore` | Interface for open/resolved conflict records |
| `SqlDelightConflictStore` | Multiplatform SQLDelight implementation — default on all platforms |
| Legacy Room conflict store | `internal`; deprecated opt-in via `useRoomPersistence()` only |
| `InMemoryConflictStore` | In-memory implementation for tests |
| `ConflictRecord` | Full JSON snapshots for debug and resolution UI |
| `ConflictSummary` | Lightweight summary for `SyncManager.conflicts` flow |

### User resolution

```kotlin
syncManager.resolveConflict("tasks", taskId, ConflictChoice.KeepLocal)
syncManager.resolveConflict("tasks", taskId, ConflictChoice.AcceptRemote)
```

---

## `dev.syncforge.sync` — Sync orchestration

**What it is:** Coordinates outbox, transport, entity handlers, and conflict resolution
into push/pull cycles.

### Public API

| Type | Stability | Description |
|------|-----------|-------------|
| `SyncManager` | Stable | `status`, `conflicts`, `sync()`, `push()`, `pull()`, `enqueueChange()`, `resolveConflict()` |
| `SyncManager.debug` / `conflictHistory` | Experimental | Debug observability streams |
| `SyncConfig` | Stable | Entity types, batch sizes, retry limits, periodic interval |
| `SyncCursorStore` | Stable | Pull cursor persistence interface |
| `SyncManagerImpl` | Internal | Default implementation |
| `SyncWorkScheduler` | Experimental | Platform hook for background scheduling (via `create()` only) |

### `SyncConfig` defaults

| Setting | Default | Notes |
|---------|---------|-------|
| `pushBatchSize` | 50 | Used by `SyncEngine` |
| `pullPageSize` | 100 | Page size for multi-page pull loops |
| `maxRetries` | 5 | Outbox tracks retries; auto-retry via `RetryScheduler` |
| `periodicSyncInterval` | 15 minutes | WorkManager minimum |
| `enableOptimisticUpdates` | `true` | Optimistic writes on `enqueueChange()` |

### Typical flow

```
enqueueChange(change)
    → captureSnapshot (handler)
    → applyOptimistic (handler → Room)
    → OutboxRepository.enqueue(payload + rollback snapshot)

sync()
    → push: outbox.peek() → transport.push() → onPushAcknowledged / rollback / markAcknowledged
    → pull: transport.pull(cursor, pageSize) → ConflictPullApplier → advance SyncCursorStore
    → update SyncStatus via StateFlow
```

### `SyncCursorStore`

| Type | Role |
|------|------|
| `SyncCursorStore` | `get()` / `set()` pull cursor |
| `InMemorySyncCursorStore` | Default for tests |
| `SharedPreferencesSyncCursorStore` | Android persistence |
| `UserDefaultsSyncCursorStore` | iOS / macOS persistence |
| `FileSyncCursorStore` | JVM desktop persistence (`~/.syncforge/`) |
| `SyncCursorStoreFactory` | Per-platform `create()` helper (Context, suiteName, or directory) |

Cursor advances from `PullResult.serverTimestampMillis` on successful pull/sync.

### Internal (not public API)

| Type | Note |
|------|------|
| `SyncEngine` | Push/pull coordinator with pagination loop |
| `OptimisticSyncCoordinator` | `enqueueChange` orchestration |
| `ConflictPullApplier` | Applies pull deltas with `ConflictPolicy` |
| `RetryScheduler` / `RetryBackoff` | Exponential backoff for failed pushes |
| `currentTimeMillis()` | `expect/actual` platform clock |

**Push rollback:** transient network failures retry with backoff; server rejections roll back individually.

---

## `dev.syncforge.debug` — Developer observability

**What it is:** In-app sync inspection API, similar to Chucker or Hyperion. Access via
`SyncManager.debug`. Intended for debug/QA builds.

### `SyncDebug` (Experimental)

| Property / method | Description |
|-------------------|-------------|
| `health: StateFlow<SyncHealth>` | Live metrics: outbox, conflicts, cursor, network |
| `outboxItems: StateFlow<List<OutboxEntry>>` | All outbox rows |
| `events: StateFlow<List<SyncEvent>>` | Ring buffer of last 100 sync events |
| `conflictRecords: StateFlow<List<ConflictRecord>>` | Full conflict audit trail |
| `clearOutbox()` | Remove all outbox entries |
| `clearEventLog()` | Reset the event ring buffer |

### `SyncHealth`

Snapshot fields: `status`, `isOnline`, `pendingOutboxCount`, `failedOutboxCount`,
`openConflictCount`, `lastSyncedAtMillis`, `pullCursorMillis`, `maxRetries`.

Events are logged automatically in `SyncManagerImpl` for sync, push, pull, enqueue, and conflicts.

---

## `dev.syncforge.compose` — Compose integration

**What it is:** Helpers and optional UI widgets for sync status, conflict resolution, and debug.

### Status observation (commonMain)

| Type | Description |
|------|-------------|
| `SyncStatusUiModel` | UI-friendly projection of `SyncStatus` |
| `collectSyncStatusUiModel()` | Composable helper to observe sync state |

| Field | Description |
|-------|-------------|
| `label` | Human-readable status string (includes conflict suffix) |
| `isSyncing` | Whether a sync cycle is running |
| `isError` | Whether the last operation failed |
| `pendingCount` | Outbox entries waiting to push |
| `conflictCount` | Open deferred conflicts |

```kotlin
@Composable
fun MyScreen(syncManager: SyncManager) {
    val uiModel = syncManager.collectSyncStatusUiModel()
    Text(uiModel.label)
}
```

### Conflict UI (androidMain)

| Composable | Description |
|------------|-------------|
| `SyncConflictChip` | Inline chip indicating a row has an open conflict |
| `SyncConflictResolutionSheet` | Bottom sheet for keep-local / accept-remote choice |

### Debug UI (androidMain, Experimental)

| Composable | Description |
|------------|-------------|
| `SyncDebugLauncher` | Floating **SF** overlay button; wraps your app content |
| `SyncDebugPanel` | Bottom sheet with Overview \| Outbox \| Conflicts \| History tabs |

```kotlin
SyncDebugLauncher(
    syncManager = syncManager,
    enabled = BuildConfig.DEBUG,
) {
    MyAppContent()
}
```

---

## `dev.syncforge.work` — Background sync

**What it is:** Platform schedulers implementing [SyncWorkScheduler](../syncforge/src/commonMain/kotlin/dev/syncforge/sync/SyncManagerImpl.kt) for periodic background `SyncManager.sync()`.

### Android (`androidMain`)

| Type | Description |
|------|-------------|
| `AndroidSyncWorkScheduler` | Registers a unique periodic WorkManager job |
| `SyncWorker` | `CoroutineWorker` that calls `SyncManager.sync()` |
| `SyncWorkerFactory` | DI-friendly worker creation |
| `SyncForgeAndroid.workManagerConfiguration` | Wires factory into `Configuration.Provider` |

```kotlin
override val workManagerConfiguration: Configuration
    get() = SyncForgeAndroid.workManagerConfiguration { syncManager }

SyncForge.android(context) {
    schedulePeriodicSyncOnStart()
}
```

### iOS (`iosMain`)

| Type | Description |
|------|-------------|
| `IosBackgroundSyncWorkScheduler` | Submits `BGAppRefreshTaskRequest` (default in `SyncForge.ios`) |
| `IosBackgroundSync` | Registers BGTaskScheduler handler, binds sync lambda |
| `registerIosBackgroundSyncTasks()` | Top-level — call from `UIApplicationDelegate` at launch |

```kotlin
SyncForge.ios {
    backgroundSyncTaskIdentifier("com.myapp.sync.refresh")
    schedulePeriodicSyncOnStart()
}
```

See [IOS_SETUP.md](IOS_SETUP.md#background-sync-bgtaskscheduler).

---

## Test modules

| Location | What is tested |
|----------|----------------|
| `commonTest/.../ChangeTest` | `Change` validation and factory methods |
| `commonTest/.../LastWriteWinsStrategyTest` | LWW conflict scenarios |
| `commonTest/.../OptimisticSyncCoordinatorTest` | Optimistic write + outbox flow |
| `commonTest/.../PullPaginationTest` | Multi-page pull loop |
| `commonTest/.../SyncForgeBuilderTest` | Builder entity type derivation |
| `androidUnitTest/.../SyncManagerImplTest` | Push cycle with in-memory outbox |
| `androidUnitTest/.../RoomOutboxRepositoryTest` | Room outbox persistence |
| `jvmTest/.../SqlDelightOutboxRepositoryTest` | SQLDelight outbox (JDBC in-memory) |
| `jvmTest/.../SqlDelightConflictStoreTest` | SQLDelight conflict store (JDBC in-memory) |
| `jvmTest/.../FileSyncCursorStoreTest` | Desktop file cursor persistence |
| `androidUnitTest/.../KtorSyncTransportTest` | REST transport push/pull parsing |
| `jvmTest/.../KtorSyncTransportAuthRefreshTest` | 401 refresh + retry, 403 no-retry |
| `androidUnitTest/.../SyncCursorStoreTest` | SharedPreferences cursor persistence |

```bash
./gradlew :syncforge:jvmTest :syncforge:testDebugUnitTest
```

---

## Additional Gradle modules

### `:mock-server`

```bash
./gradlew :mock-server:run
```

JVM Ktor server on port `8080` (override with `PORT` env var). Implements
[REST_API.md](REST_API.md) including `POST /dev/simulate-edit` for conflict demos.

### `:sample`

```bash
./gradlew :sample:installDebug
```

Android Compose Tasks app. Points at `http://10.0.2.2:8080` on the emulator.
Demonstrates `SyncForge.android { }`, conflict resolution, and `SyncDebugLauncher`.

---

## What comes next — Phase 7

See [ROADMAP.md](ROADMAP.md) for the full plan. Phase 6 (`0.6.0`) **complete**:

- ✅ iOS target, Darwin Ktor, SQLDelight persistence, `SyncForge.ios { }`
- ✅ SQLDelight Android default; legacy Room internals removed from public API
- ✅ JVM desktop (`SyncForge.desktop { }`) and native macOS (`SyncForge.macos { }`)
- ✅ SKIE / Swift API polish on `:syncforge` and `:sample-ios-shared`
- ✅ Automatic Room → SQLDelight data migrator (`RoomToSqlDelightMigrator`)
- ⬜ Maven Central publication (Phase 7)
- ⬜ DataStore cursor persistence