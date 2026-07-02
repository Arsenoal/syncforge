# Changelog

All notable changes to SyncForge are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Multi-entity E2E tests** — `MultiEntityE2ETest` (task + note flows, conflict isolation) via `./gradlew androidE2e`
- **CI `android-e2e` job** — GitHub Actions emulator + `:mock-server` runs connected instrumented tests
- **`:syncforge-android-deps`** — bundles Room, kotlinx-serialization, and WorkManager as transitive `api` deps for Android consumers
- **`dev.syncforge.android` Gradle plugin** — applies KSP (SyncForge + Room compiler) and Kotlin serialization; consumers no longer declare those manually

## [0.6.0-SNAPSHOT] - 2026-06-30

### Added

- **SQLDelight is now the Android default** — `SyncForge.android { }` uses `syncforge.db` without `useSqlDelightPersistence()`
- **`RoomToSqlDelightMigrator`** — automatic one-time migration of outbox + conflict rows from `syncforge_outbox.db`
- **`useRoomPersistence()`** — legacy Room opt-in (deprecated)
- **`:sample` E2E tests** — `TasksE2ETest` (UiAutomator) against `:mock-server`; run via `./gradlew androidE2e`

### Changed

- `useSqlDelightPersistence()` deprecated (no-op; SQLDelight is already default)
- Legacy Room outbox/conflict types marked `internal` — not part of the public API
- Version bumped to `0.6.0-SNAPSHOT`

### Documentation

- Synced README, Getting Started, Roadmap, KMP Migration, Module Reference, and iOS Setup for 0.6.0 SQLDelight default

### Added (earlier unreleased)

#### API stability (pre-1.0)

- `@ExperimentalSyncForgeApi` moved to `:syncforge-annotations` (KMP) — shared across `:syncforge` and `:syncforge-persistence`
- `@ExperimentalSyncForgeApi` on `:syncforge-persistence` factories (`createSyncForgePersistence`, `SyncForgePersistenceFactory`, `createDefaultSyncForgePersistence`)

#### Kotlin Multiplatform persistence (M2–M4)

- `:syncforge-persistence` — SQLDelight schemas + platform drivers (`SyncForgePersistence.create()`)
- `:syncforge` `syncPersistenceMain` — `SqlDelightOutboxRepository`, `SqlDelightConflictStore`, `outboxRepository()` / `conflictStore()` extensions
- `SyncForge.ios { }` — SQLDelight outbox + conflicts by default; `UserDefaults` cursor; `IosNetworkMonitor`
- `SyncForge.android { useSqlDelightPersistence() }` — Android SQLDelight opt-in (Room remains default)
- `SyncForgePersistenceFactory.create(context)` — Android persistence entry point
- `createSyncForgePersistence(context)` — lower-level Android factory
- `:sample-ios-shared` — `IosSampleController` Swift-friendly sample facade
- `ios-sample/` — SwiftUI Xcode app wired to `:sample-ios-shared`
- JVM tests for SQLDelight repositories (`:syncforge:jvmTest`)
- `SyncForge.desktop { }` — JVM desktop DSL with SQLDelight, file cursor, OkHttp transport
- `SyncForge.macos { }` — native macOS entry point (`macosArm64` / `macosX64` targets)
- `FileSyncCursorStore` — persisted pull cursor at `~/.syncforge/`
- SKIE Gradle plugin on `:syncforge` and `:sample-ios-shared` for improved Swift interop
- `IosSampleController.observeStatusLabel()` — SKIE Flow export for Swift

### Deprecated

- `ConflictResolver`, `LastWriteWinsResolver`, `ConflictStrategies.fromResolver()` — migrate to `ConflictPolicy` + `ConflictStrategies`; **removed in 1.0**
- `TypedEntitySyncHandler(legacyResolver)` constructor — use parameterless constructor; configure via `conflicts { }`

### Changed

- `:syncforge-annotations` converted to Kotlin Multiplatform (hosts `@ExperimentalSyncForgeApi` alongside KSP annotations)
- Resolved circular dependency between `:syncforge` and `:syncforge-persistence` by splitting schemas/drivers from repository implementations
- `KtorSyncTransport` lives in `commonMain` with platform HTTP engines

### Documentation

- [ANDROID_SETUP.md](docs/ANDROID_SETUP.md) — Room vs SQLDelight, migration cutover guide
- [IOS_SETUP.md](docs/IOS_SETUP.md) — iOS DSL, SKIE, and Swift integration
- [DESKTOP_SETUP.md](docs/DESKTOP_SETUP.md) — JVM desktop and native macOS setup
- [KMP_MIGRATION.md](docs/KMP_MIGRATION.md) — milestone progress through M5

---

## [0.4.0] - 2026-06-29

Phase 5 — developer experience, conflict strategies, and in-app observability.

### Added

#### Ergonomic setup (`dev.syncforge`)

- `SyncForge.android(context) { }` — Android DSL with Room outbox, persisted cursor, connectivity monitoring, Ktor transport, retry, and WorkManager defaults
- `SyncForgeBuilder` / `SyncForge.builder { }` — fluent builder; auto-derives `entityTypes` from registered handlers
- `SyncForgeAndroid.workManagerConfiguration { syncManager }` — one-liner WorkManager `Configuration.Provider` wiring
- `EntityRegistry.of(...)` — convenience factory for handler lists
- KSP now generates `SyncForgeHandlers` with `registry(dao)` and per-entity factory functions

#### Conflict resolution (`dev.syncforge.conflict`)

- `ConflictPolicy` + `conflicts { }` builder — global default (last-write-wins) with per-entity overrides
- `ConflictStrategies` — `lastWriteWins()`, `alwaysLocal()`, `alwaysRemote()`, `deferToUser()`, `merge { }`
- `MergeScope` helpers — `preferNewer()`, `preferLocal()`, `preferRemote()`
- `ConflictDetector`, `ConflictPullApplier` — centralized pull-time conflict detection and resolution
- Room table `syncforge_conflicts` — persisted conflict records survive process death
- `SyncManager.conflicts`, `conflictHistory`, `resolveConflict()`, `findOpenConflict()`
- `SyncStatus.Pending.conflictCount` — surfaces open deferred conflicts in status UI

#### Compose UI (`dev.syncforge.compose`)

- `SyncConflictChip` — inline conflict indicator for list rows
- `SyncConflictResolutionSheet` — bottom sheet for keep-local / accept-remote resolution
- `SyncStatusUiModel.conflictCount` — conflict-aware status labels

#### Sync debug console (`dev.syncforge.debug`)

- `SyncManager.debug: SyncDebug` — developer observability API (Chucker/Hyperion-style)
- `SyncHealth` — live metrics: outbox counts, open conflicts, cursor, network, retry limits
- `SyncEvent` ring buffer — last 100 sync/push/pull/enqueue/conflict events
- `SyncDebugLauncher` — floating **SF** overlay button (debug builds)
- `SyncDebugPanel` — bottom sheet with Overview | Outbox | Conflicts | History tabs
- Manual actions: Sync, Push, Pull, Clear outbox (with confirmation)

#### Outbox & database

- `OutboxRepository.observeAll()` — full outbox observation for debug panels
- `SyncForgeDatabase` version 3 — adds `syncforge_conflicts` table (destructive migration on upgrade)

#### Mock server & sample

- `POST /dev/simulate-edit` — simulates a concurrent server edit for conflict demos
- `InMemorySyncStore.forceUpdate()` — dev helper backing the simulate-edit endpoint
- Sample app: `DevSyncClient`, conflict resolution in `TasksViewModel` / `TasksScreen`, `SyncDebugLauncher` wrapper

### Changed

- `SyncForge.create()` / `createWithRetry()` accept `conflictPolicy` and `conflictStore`
- Pull conflicts route through `ConflictPolicy` instead of per-handler `ConflictResolver` only
- `TypedEntitySyncHandler.withSyncState()` made public for conflict resolution flows
- Sample `Application` setup reduced from ~45 lines to ~15 lines via `SyncForge.android { }`

### Backward compatible

- `SyncForge.create()` / `createWithRetry()` unchanged for existing callers
- Legacy `ConflictResolver` still supported via `ConflictStrategies.fromResolver()`

[0.4.0]: https://github.com/syncforge/syncforge/releases/tag/v0.4.0

## [0.3.0] - 2026-06-29

Phase 4 — production readiness and developer experience.

### Added

#### Retry & resilience (`dev.syncforge.sync`)

- `RetryBackoff` — exponential backoff calculator
- `RetryScheduler` / `InProcessRetryScheduler` — schedules push retries after failures
- `SyncForge.createWithRetry()` — convenience factory with in-process retry scheduler
- `SyncErrorPolicy` — maps error codes to retryable vs permanent failures
- `OutboxEntry.nextRetryAtMillis` — backoff scheduling per outbox row
- `SyncStatus.Offline` — device offline with queued changes
- `SyncStatus.Pending.permanentlyFailedCount` — surfaces exhausted retries

#### Network awareness (`dev.syncforge.network`)

- `NetworkMonitor` / `AlwaysOnlineNetworkMonitor` — connectivity observation contract
- `AndroidNetworkMonitor` + `NetworkMonitorFactory` — `ConnectivityManager` integration
- Auto-push on network reconnect

#### Pull pagination

- `SyncTransport.pull()` accepts `pageSize` and `pageCursor`
- `PullResult.hasMore` / `nextPageCursor` — multi-page pull loop in `SyncEngine`
- Mock server supports `limit` and `cursor` query params

#### Auth & HTTP errors (`dev.syncforge.network`)

- `SyncAuthProvider` — bearer token injection for `KtorSyncTransport`
- `HttpStatusMapper` — maps HTTP status codes to `SyncError.Code`
- `SyncTransportException` — structured transport failures

#### KSP codegen

- `:syncforge-annotations` — `@SyncForgeEntity`, `@SyncForgeDao`
- `:syncforge-ksp` — generates `TypedEntitySyncHandler` implementations
- Sample app uses generated `TaskEntitySyncHandler`

#### WorkManager

- `SyncWorkScheduler.scheduleRetry()` — one-off retry work requests
- `SyncWorkerFactory` — DI-friendly worker creation
- Sample app wires periodic + retry sync via WorkManager

#### Tests

- `RetryBackoffTest`, `PullPaginationTest`
- `InMemorySyncStoreJvmTest` — mock-server pagination

### Changed

- Transient network push failures no longer roll back optimistic writes — entries retry with backoff
- Server rejections still roll back; validation/auth errors mark entries permanently failed
- `SyncForge.create()` accepts `networkMonitor`, `retryScheduler`, `workScheduler`
- Outbox DB version 2 (`nextRetryAtMillis` column; destructive migration on upgrade)
- `RoomOutboxRepository` respects `maxRetries` for peek/query semantics

[0.3.0]: https://github.com/syncforge/syncforge/releases/tag/v0.3.0

## [0.2.0] - 2026-06-29

Phase 3 — Ktor reference transport, persisted sync cursor, mock server, and sample app.

### Added

#### Network (`dev.syncforge.network`)

- `KtorSyncTransport` — reference `SyncTransport` using Ktor OkHttp client
- `dev.syncforge.network.api` — shared REST DTOs (`PushRequest`, `PushResponse`, `PullResponse`, etc.)

#### Sync cursor (`dev.syncforge.sync`)

- `SyncCursorStore` — interface for persisting the pull cursor
- `InMemorySyncCursorStore` — test/default implementation
- `SharedPreferencesSyncCursorStore` + `SyncCursorStoreFactory` — Android persistence
- `SyncResult.Success.syncCursorMillis` — server timestamp from successful pulls

#### Modules

- `:mock-server` — JVM Ktor server for local development (`POST /sync/push`, `GET /sync/pull`)
- `:sample` — Android Compose Tasks demo app wired to `KtorSyncTransport`

#### Tests

- `KtorSyncTransportTest` — MockEngine push/pull contract tests
- `SyncCursorStoreTest` — SharedPreferences cursor persistence (Robolectric)

### Changed

- `SyncForge.create()` accepts optional `cursorStore` (defaults to in-memory)
- `SyncManagerImpl` loads and persists pull cursor via `SyncCursorStore`
- Pull cursor advances using `serverTimestampMillis` from transport responses
- `:syncforge` adds a JVM target (shared `commonMain` for mock-server)

### Documentation

- `docs/ROADMAP.md` — capabilities, limitations, Phase 4–6 plan
- `docs/REST_API.md` — HTTP contract for backend implementers
- Updated `README.md` and `docs/MODULES.md` for v0.2.0 accuracy

### Not yet included

- `@SyncedEntity` KSP code generation (planned Phase 4)
- Automatic push retry / exponential backoff (planned Phase 4)
- Network-aware sync triggers (planned Phase 4)
- Pull pagination (planned Phase 4)

[0.2.0]: https://github.com/syncforge/syncforge/releases/tag/v0.2.0

## [0.1.0] - 2026-06-29

Phase 2 — Room outbox, optimistic updates with rollback, and pull delta application.

### Added

#### Entity handlers (`dev.syncforge.entity`)

- `EntitySyncHandler` — per-entity-type bridge to your Room DAOs
- `TypedEntitySyncHandler<T>` — abstract base with JSON, optimistic apply, rollback, and pull wiring
- `EntityRegistry` — maps entity type strings to handlers
- `PullApplyOutcome` — result enum for pull operations

#### Room outbox (`dev.syncforge.outbox`)

- `OutboxEntryEntity` — Room table `syncforge_outbox`
- `OutboxDao` — CRUD + `Flow` observation
- `SyncForgeDatabase` — dedicated outbox database (separate from your app schema)
- `RoomOutboxRepository` — persistent outbox implementation
- `SyncForgeDatabaseFactory` — `create()`, `createInMemory()`, `createOutboxRepository()`
- `InMemoryOutboxRepository` moved to `commonMain` (shared with tests)

#### Sync orchestration (`dev.syncforge.sync`)

- `OptimisticSyncCoordinator` — snapshot → optimistic Room write → outbox enqueue
- `PullDeltaApplier` — applies `RemoteDelta` list via entity handlers + conflict resolution
- `SyncEngine` now rolls back optimistic writes on push failure/rejection
- `SyncEngine` marks entities `SYNCED` on push acknowledgement
- `SyncEngine` applies pull deltas with conflict resolution
- `OutboxRepository.countPending()` — accurate pending count for `SyncStatus`
- `OutboxEntry.rollbackSnapshotJson` — stores pre-optimistic snapshot for rollback

#### API changes

- `SyncForge.create()` now requires `EntityRegistry`
- `OutboxRepository.enqueue()` accepts `payloadJson` and `rollbackSnapshotJson`
- `SyncManagerImpl` requires `EntityRegistry` (breaking change from 0.0.1)

#### Tests

- `OptimisticSyncCoordinatorTest` — snapshot + optimistic + outbox persistence
- `FakeEntitySyncHandler` — test double for handler contract
- `RoomOutboxRepositoryTest` — Room in-memory persistence (Robolectric)
- Updated `SyncManagerImplTest` for entity registry + push acknowledgement

### Changed

- Push network failures roll back all entries in the failed batch
- Partial push rejections roll back only rejected entries

## [0.0.1] - 2026-06-29

First public skeleton release. Establishes the architecture, public API surface, and test
foundation for offline-first Room sync. Not yet production-complete — several features are
interfaces and stubs that will be filled in during Phase 2.

### Added

#### Project & build

- Kotlin Multiplatform library module `:syncforge` with an Android target
- Gradle version catalog (`gradle/libs.versions.toml`) for dependency management
- Kotlin 2.1.10, Coroutines, kotlinx.serialization, Room, WorkManager, and Ktor dependencies
- Compose Runtime integration for sync status observation in UI layers
- KSP plugin wired for upcoming Room entity / annotation processing (Phase 2)

#### Core API (`dev.syncforge`)

- `SyncForge.create()` — single entry point to build a configured `SyncManager`

#### Model layer (`dev.syncforge.model`)

- `SyncState` — per-entity local sync state (`SYNCED`, `PENDING`, `CONFLICT`, `FAILED`)
- `ChangeType` — mutation kind (`CREATE`, `UPDATE`, `DELETE`)
- `Change` — typed local mutation with factory helpers (`create`, `update`, `delete`)
- `OutboxEntry` — serializable outbox row representation
- `SyncStatus` — observable sync lifecycle for UI (`Idle`, `Syncing`, `Pending`, `LastSynced`, `Error`)
- `SyncResult` / `SyncError` — structured outcomes and error codes from sync operations

#### Entity contract (`dev.syncforge.entity`)

- `SyncedEntity` — interface that Room entities implement for sync metadata columns
- `RemoteMetadata` — server-side version and timestamp metadata on pulled deltas

#### Outbox (`dev.syncforge.outbox`)

- `OutboxRepository` — interface for enqueueing, observing, and acknowledging pending changes
- `InMemoryOutboxRepository` (Android) — in-memory implementation for tests and prototyping

#### Network (`dev.syncforge.network`)

- `SyncTransport` — pluggable push/pull boundary (bring your own Ktor, Retrofit, etc.)
- `PushResult`, `PullResult`, `RemoteDelta` — transport-level data types
- `NoOpSyncTransport` — no-network stub for tests and offline-only development

#### Conflict resolution (`dev.syncforge.conflict`)

- `ConflictResolver` — interface for custom merge strategies
- `ConflictResolution` — `KeepLocal`, `AcceptRemote`, `Merged`, `DeleteLocal` outcomes
- `LastWriteWinsResolver` — default resolver comparing `updatedAtMillis`

#### Sync orchestration (`dev.syncforge.sync`)

- `SyncManager` — primary public API (`sync`, `push`, `pull`, `enqueueChange`, `status`)
- `SyncManagerImpl` — default implementation with mutex-guarded sync cycles
- `SyncConfig` — runtime configuration (entity types, batch sizes, retry limits, intervals)
- `SyncEngine` — internal push/pull coordinator (not part of the public API)
- `SyncWorkScheduler` — platform hook for background sync scheduling

#### Compose (`dev.syncforge.compose`)

- `SyncStatusUiModel` — UI-friendly projection of `SyncStatus`
- `collectSyncStatusUiModel()` — Composable helpers to observe sync state from `SyncManager`

#### Android background sync (`dev.syncforge.work`)

- `AndroidSyncWorkScheduler` — WorkManager periodic sync registration
- `SyncWorker` — CoroutineWorker that delegates to `SyncManager.sync()`

#### Tests

- `ChangeTest` — validates `Change` invariants and factory methods
- `LastWriteWinsResolverTest` — conflict resolution scenarios
- `SyncManagerImplTest` — push cycle integration test with in-memory outbox

### Not yet included (planned for Phase 2+)

- Room-backed persistent outbox
- Optimistic Room writes with automatic rollback on push failure
- Pull delta application to entity DAOs
- Per-entity JSON serializers (`EntitySerializer`)
- Ktor `SyncTransport` reference implementation
- `@SyncedEntity` KSP code generation
- Sample application

[0.1.0]: https://github.com/syncforge/syncforge/releases/tag/v0.1.0
[0.0.1]: https://github.com/syncforge/syncforge/releases/tag/v0.0.1