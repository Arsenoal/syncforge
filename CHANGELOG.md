# Changelog

All notable changes to SyncForge are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **GraphQL schema + resolver recipes (1.4-09)** — `syncforge-server/graphql/syncforge-sync.graphql`, Ktor/Apollo/Spring resolver guides, `:backend-starter-graphql`, RECIPES.md sections for GraphQL transport, BYO `SyncDeltaStore`, and custom `SyncTransport`
- **`:syncforge-transport-graphql` (1.4-08)** — `GraphQlSyncTransport` over GraphQL-over-HTTP (`syncPush` / `syncPull`); `:mock-server` and `:syncforge-server` `/graphql` facade for local push/pull
- **Multi-device Android E2E (1.4-06)** — two-emulator phase tests (`MultiDeviceE2ETest`) with mock-server session coordination; task gitLike conflict + tag LWW scenarios; `androidMultiDeviceE2e` Gradle task + nightly CI workflow
- **Backend contract test kit (1.4-07)** — `SyncPushPullContract` (REST DTO) + `SyncDeltaStoreContract` with `InMemorySyncBackend`; Supabase + Firebase + JDBC `SyncStore` run shared scenarios
- **`:syncforge-catalog` (1.4-05)** — published Gradle version catalog pinning all SyncForge library artifacts and `studio.syncforge.android`; BOM-free consumer setup in GETTING_STARTED + consumer-smoke
- **`:syncforge-transport-firebase` (1.4-04)** — `FirebaseSyncDeltaStore` via Cloud Functions HTTPS (`syncforgePush` / `syncforgePull`); Firestore schema + listener patterns
- **`:syncforge-transport-supabase` (1.4-03)** — `SupabaseSyncDeltaStore` via PostgREST RPC (`syncforge_push` / `syncforge_pull`); SQL migration + Realtime patterns
- **`SyncDeltaStore` + `DeltaStoreSyncTransport` (1.4-02)** — `:syncforge-transport-core` optional BOM artifact; BaaS storage port + single `SyncTransport` adapter for Firebase/Supabase/custom backends
- **Spring Boot backend starter (1.4-01)** — `:backend-starter-spring` with `SyncHandlers`-backed REST routes, in-memory or `JdbcSyncStore`, Flyway schema, `docker-compose` Postgres quickstart; `JdbcSyncStore` in `:syncforge-server`
- **Compose Multiplatform conflict UI (1.3-05)** — `SyncConflictChip` + `SyncConflictResolutionSheet` in `:syncforge` `composeMain` (Android, JVM, Apple); `:sample-desktop:runComposeConflictDemo`; [COMPOSE_UI.md](docs/COMPOSE_UI.md)
- **`:sample` conflict policy settings (1.2-10)** — Policy tab with per-entity strategy dropdowns (notes/tags/tasks), DataStore persistence, `updateConflictPolicy()` on change; `ConflictSettingsE2ETest` validates runtime switch to defer-to-user
- **`docs/SWIFT_INTEROP.md`** — SKIE Swift patterns: Flow collection, suspend/async, callbacks, error handling, reference table (1.3-07)

### Fixed

- **Android E2E conflict tests on CI** — `tapTaskLocalEdit` / `tapTagLocalEdit` wait and scroll to off-screen buttons after server-edit banner; `selectConflictKind` waits for policy persistence; tags conflict label is `Conflict` (not tasks' `Conflict — tap Resolve`)
- **`multiEntity_taskConflict_noteStillSyncs` E2E flake** — `waitForRowSyncState` uses passive semantics reads inside `waitUntil` (avoids `performMeasureAndLayout` during layout)
- **`KotlinFlowInterop.swift`** — reusable SKIE Flow collector; `ios-sample` observes status via `observeStatusLabel()` AsyncSequence
- **`:sample-desktop`** — JVM CLI sample proving `SyncForge.desktop { }` against `:mock-server`; `desktopE2e` in CI (1.3-01)

### Changed

- **Manual GitHub releases** — tag push no longer triggers Publish Release CI or creates GitHub Releases; maintainers draft releases in the UI and optionally run **Actions → Publish Release** (`workflow_dispatch`). See [RELEASE.md](docs/RELEASE.md).
- **Maven Central publish gated to 2.0.0+** — pre-2.0 tags run macOS compile/test only; `publishAllToMavenCentral` skips Sonatype upload for 1.x (override: `-PallowPre2MavenCentralPublish=true` for maintainers). Integrate 1.x via git + `publishAllToMavenLocal`. See [MAVEN_PUBLISH.md](docs/MAVEN_PUBLISH.md).
- **iOS SPM / XCFramework gated to 2.0.0+** — `publishIosSpmArtifacts` placeholder with same version gate as Maven Central; until 2.0 use KMP frameworks per [IOS_SETUP.md](docs/IOS_SETUP.md) (1.3-04)
- **No semver rollouts until 2.0.0** — development on `main` only; no new tags/GitHub Releases/Maven/SPM until `v2.0.0` ([RELEASE.md](docs/RELEASE.md))
- **iOS DSL graduated to stable (1.3-02)** — `SyncForge.ios { }`, `IosBackgroundSync`, and `registerIosBackgroundSyncTasks` no longer require `@OptIn(ExperimentalSyncForgeApi::class)`; `persistence()` and `customize()` remain experimental (parity with Android)
- **Desktop / macOS DSLs graduated to stable (1.3-03)** — `SyncForge.desktop { }` and `SyncForge.macos { }` no longer require opt-in; stable `databaseName()` on desktop DSL (Android parity); `persistence()` / `customize()` remain experimental
- **`StableIosApiSurfaceSourceTest`**, **`StableDesktopApiSurfaceTest`**, **`StableMacosApiSurfaceSourceTest`** — CI guards for platform DSL stability
- **`MODULES.md`** — iOS, desktop, and macOS platform rows marked Stable
- **`:sample-desktop`** — uses stable `databaseName()` instead of experimental `persistence()` override

## [1.2.0] - 2026-07-08

**Conflict resolution** minor release — merge-base snapshots, `gitLike { }` three-way merge,
experimental `crdt { }`, strategy catalog, outbox reconciliation, and a `:sample` per-entity
policy matrix with instrumented E2E coverage. No breaking changes to 1.0 / 1.1 stable APIs.

**Upgrade from `1.1.0`:** bump coordinates to `1.2.0`. Opt in to new strategies with
`@OptIn(ExperimentalSyncForgeApi::class)` for `gitLike { }` and `crdt { }`. Existing
`conflicts { }` wiring (LWW, `alwaysRemote`, `merge { }`, `deferToUser()`) is unchanged.

### Added

- **`MergeBaseStore` / `MergeBaseRecorder`** — last-synced JSON per `(entityType, entityId)` on push ack and pull apply; feeds `gitLike` three-way merge (1.2-07)
- **`gitLike { }` strategy** — `threeWayMerge { base, local, remote -> … }` with `onUnmergeable { }` and `onRemoteDelete { }` fallbacks (1.2-08, `@ExperimentalSyncForgeApi`)
- **CRDT primitives** — `LwwRegister<T>`, `OrSet<T>`, `GCounter` in `commonMain` (1.2-01)
- **`crdt { }` strategy** — per-field CRDT merge for `@Serializable` entities (1.2-02, `@ExperimentalSyncForgeApi`)
- **`ConflictStrategyKind` catalog** — enum + `ConflictStrategies.fromKind()` + `conflictPolicyFromKinds()` for static and runtime per-entity assignment (1.2-09)
- **`SyncManager.updateConflictPolicy()`** — replace conflict policy at runtime via `MutableConflictPolicy` (1.2-10)
- **`OutboxReconciler`** — align outbox with resolution outcomes (`AcceptRemote` clears stale rows; `Merged` replaces with reconciled `UPDATE`; `KeepLocal` retains entries) (1.2-11)
- **KSP field-merge codegen** — `@LastWriteWins`, `@ObservedRemoveSet`, `@GrowOnlyCounter` on entity properties generate `*FieldMerge` helpers (1.2-03)
- **Tombstone-aware merge recipes** — `MergeEntityBuilder.onRemoteDelete { }` and docs for delete vs update conflicts (1.2-04)
- **`SyncEntityJson`** — patch `localVersion` in serialized entity JSON when kotlinx.serialization omits default fields
- **Trailing push in `SyncEngine.runFullSync()`** — second push when pull reconciliation enqueues new outbox work (e.g. git-like auto-merge)
- **`:sample` conflict matrix** — `sampleEntityConflicts()`: notes `alwaysRemote()`, tags `lastWriteWins()`, tasks `gitLike` (`SampleConflictPolicies.kt`)
- **`ConflictStrategyE2ETest`** — 11 instrumented tests (per-strategy + multi-entity isolation) via `./gradlew androidE2e` (28 tests total; 1.2-05)
- **`CONFLICT_RESOLUTION.md` v2** — per-entity matrix, git-like flow, catalog, outbox reconcile, E2E map (1.2-06)
- **Unit / integration tests** — `GitLikeMergeStrategyTest`, `CrdtMergeStrategyTest`, `ConflictStrategyPullApplierE2ETest`, `OutboxReconcileTest`, `MergeBasePullApplierTest`, `UpdateConflictPolicyTest`, CRDT primitive tests

### Changed

- **`ConflictPullApplier`** — aligns `localVersion` to pulled `serverVersion` on `AcceptRemote`; uses `serverVersion + 1` when a reconciled `Merged` row must be pushed
- **`ConflictResolutionService`** — passes `remoteServerVersion` into outbox reconciliation on user resolve

### Fixed

- **Auto-merge sync state** — git-like merge + outbox reconcile no longer leaves rows stuck `Pending` when OCC expected a newer `localVersion` after mock-server simulate-edit
- **Delete-conflict E2E** — stabilized Accept Remote resolution flow in `SampleScenariosE2ETest`
- **Multi-entity E2E** — `row_sync_state_*` test tags and timeouts for independent note/tag sync under task conflicts

### Distribution

- **Git tag `v1.2.0`** — triggers [Publish Release](.github/workflows/publish-release.yml) to Maven Central (`studio.syncforge`); version taken from tag

## [1.1.0] - 2026-07-07

**Wire-up** minor release — pluggable entity stores, injectable Ktor `HttpClient`, optional DI modules, encrypted token storage, DataStore pull cursor. No breaking changes to 1.0 stable APIs.

**Upgrade from `1.0.0`:** bump coordinates to `1.1.0`. Existing tokens and sync cursors migrate automatically on first launch. Optional artifacts (`syncforge-store-*`, `syncforge-integration-*`) are BOM-listed but not transitive — add explicitly when needed.

### Added

- **`SyncHttpClient`** — REST push/pull contract decoupled from Ktor (`commonMain`)
- **`RestSyncTransport`** — transport implementation over `SyncHttpClient`; `KtorSyncTransport` delegates through it (backward compatible)
- **`:syncforge-network-ktor`** — optional Ktor adapter (`KtorSyncHttpClient`, platform engines); listed in BOM, not transitive in `:syncforge`
- **`httpClient(client)`** — inject app-owned Ktor `HttpClient` on Android / iOS / desktop DSLs
- **`EntityStore`** — pluggable entity persistence contract in `commonMain`; `EntitySyncHandler` delegates through it
- **`@SyncForgeStore`** — KSP annotation generating handlers from `EntityStore` implementations
- **`:syncforge-store-room`** — Room DAO → `EntityStore` adapter (optional BOM artifact)
- **`:syncforge-store-inmemory`** — thread-safe in-memory `EntityStore` for tests and prototyping (optional BOM artifact)
- **`:syncforge-integration-koin`** — optional `syncForgeModule { }` and `syncForgeWorkManagerConfiguration()` helpers
- **`:syncforge-integration-hilt`** — optional `SyncForgeHilt` factory helpers for `@Provides` wiring
- **Encrypted `TokenStore`** — Android default uses `EncryptedSharedPreferences` (Keystore-backed); migrates legacy plain `syncforge_auth_tokens` prefs on first read
- **Keychain `TokenStore`** — iOS/macOS default; migrates legacy `syncforge.auth.*` UserDefaults keys on first read
- **`login(email, password: CharArray)` / `register(email, password: CharArray)`** — password wiped in `finally` after the request body is built; `credentialFields()` in `auth { }` for custom JSON keys
- **`DataStoreSyncCursorStore`** — Android default pull cursor via DataStore Preferences; migrates legacy SharedPreferences cursor on first read
- **`androidx.datastore:datastore-preferences`** — Android-only dependency for cursor persistence
- **`TokenStoreTest`** — Robolectric coverage for encrypted persistence, legacy migration, and clear
- **`verifyBomConstraints`** — Gradle check that optional 1.1 modules are BOM-listed
- **RECIPES.md** — injectable HttpClient, BYO store, Koin + Hilt DI sections

### Changed

- **Built-in auth graduated to stable** — `AndroidSyncForgeDsl.auth { }`, `SyncManager.authState` / `session` / `register` / `login` / `logout` no longer require `@OptIn(ExperimentalSyncForgeApi::class)`
- **`StableApiSurfaceTest` / `StableAndroidApiSurfaceTest`** — auth members included in stable API guards
- **`SyncCursorStoreFactory.create(context)`** — returns `DataStoreSyncCursorStore` instead of `SharedPreferencesSyncCursorStore` (override with `cursorStore(...)` still supported)
- **`syncforge-bom`** — constraints for `syncforge-network-ktor`, `syncforge-store-room`, `syncforge-store-inmemory`, `syncforge-integration-koin`, `syncforge-integration-hilt`
- **Maven Central artifact verification** — optional 1.1 modules included in `verifyMavenCentralArtifacts`

### Distribution

- **Maven Central `1.1.0`** — full artifact set published under `studio.syncforge` (core + optional store/integration/network modules; KMP native targets)

## [1.0.0] - 2026-07-07

First **semver-stable** public release. Stable without `@OptIn(ExperimentalSyncForgeApi::class)`:

- `SyncForge.android { }`, `SyncForgeAndroid.workManagerConfiguration`
- Core `SyncManager` — sync lifecycle, outbox, conflicts, scheduling hooks
- `ConflictPolicy`, `conflicts { }`, `ConflictChoice`, `resolveConflict()`
- Compose production UI — `SyncStatusUiModel`, conflict chip/sheet

REST push/pull contract (**v1**) is frozen per [REST_API.md](docs/REST_API.md). Auth, debug, KMP platform DSLs, and `SyncForgeBuilder` remain experimental.

**Upgrade from `0.9.0-rc.5`:** stable API unchanged; bump coordinates to `1.0.0`. Remove `useRoomPersistence()` if still present (removed in 1.0).

### Added

- **`verifySignOffMatrix`** Gradle task — runs `verifyReleaseSignOff` + `verifyConsumerSmokeMavenCentral` (1.0-P0-04 automated soak checks)
- **`.github/scripts/run-sign-off-matrix.sh`** — local runner with CI E2E reminder
- **`SyncEngineIntegrationTest`** (P1-04) — retry exhaustion, multi-page pull, and offline queue scenarios in `commonTest`
- **Docs freeze** (P0-06) — `CHANGELOG.md`, `docs/MODULES.md`, and `docs/GETTING_STARTED.md` aligned with 1.0 stable API boundaries

### Changed

- **Publish Release workflow** — always runs `publishAllToMavenCentral`; tag or `workflow_dispatch` with version tag only
- **Maven Central** — `1.0.0` artifact set under `studio.syncforge` (BOM, KMP targets, KSP, Gradle plugin `studio.syncforge.android`)

### Fixed

- **Maven Central publish** — `publishAllToMavenCentral` targets `*MavenCentralRepository` tasks explicitly; CI verifies required POMs on `repo1.maven.org`
- **Supplemental publish scripts** — Bash 3.2 compatibility on macOS CI (`declare -A` / `mapfile` removed)

## [0.9.0-rc.5] - 2026-07-06

### Added

- **Maven Central** — full `0.9.0-rc.5` artifact set published under `studio.syncforge` (39 components validated)

### Changed

- **API stability (1.0)** — stable surfaces no longer require `@OptIn(ExperimentalSyncForgeApi::class)`:
  `SyncForge.android`, `SyncForgeAndroid.workManagerConfiguration`, core `SyncManager` (sync/outbox/conflicts),
  `ConflictPolicy` / `conflicts { }`, `SyncStatusUiModel` and production Compose helpers.
  Auth, debug, KMP platform DSLs, `SyncForgeBuilder`, and custom persistence remain experimental.
- **`@ExperimentalSyncForgeApi`** — message updated for post-1.0 semver (experimental APIs may change in minor releases).

### Added

- **`StableApiSurfaceTest`** / **`StableAndroidApiSurfaceTest`** — reflection guards for stable vs experimental API boundaries.

### Removed

- **`useRoomPersistence()`** — legacy Room opt-in for SyncForge outbox/conflicts removed for 1.0.
  SQLDelight is the only backend; automatic `RoomToSqlDelightMigrator` on upgrade is unchanged.
  **Upgrade:** remove `useRoomPersistence()` from `SyncForge.android { }` — no replacement needed.

## [0.9.0-rc.4] - 2026-07-03

### Fixed

- **Maven Central** — full artifact set for `studio.syncforge` (persistence, JVM, Gradle plugin, plugin marker, and root KMP metadata) after incomplete `0.9.0-rc.3` staging

## [0.9.0-rc.3] - 2026-07-03

### Changed

- **Gradle plugin id** — `dev.syncforge.android` → `studio.syncforge.android` (aligns with Maven group `studio.syncforge`; Kotlin packages remain `dev.syncforge.*`)

## [0.9.0-rc.1] - 2026-07-02

### Added

- **Built-in auth (experimental)** — `auth { }` DSL on Android/iOS/desktop, `SyncManager.register`/`login`/`logout`, `authState`, configurable token JSON field mapping, `TokenStore`, `SyncForgeAuthService`, `docs/AUTH_API.md` (Android flow + mermaid diagram)
- **`:backend-starter` auth** — `POST /auth/register`, `/auth/login`, `/auth/refresh`; Bearer-protected sync routes
- **Room → SQLDelight migration sign-off tests** — `SyncForgeAndroidMigrationTest` (Robolectric), `RoomMigrationInstrumentedTest` (sample/androidTest, runs in `androidE2e`), and `MigrationTestSupport` helpers
- **`verifyReleaseSignOff`** Gradle task — JVM + Android unit tests, server tests, consumer smoke, compile checks (CI `linux` job)
- **`:syncforge-server`** — shared Ktor sync routes (`POST /sync/push`, `GET /sync/pull`), `SyncStore` contract, and `InMemorySyncStore`
- **`:backend-starter`** — minimal runnable reference backend (`./gradlew :backend-starter:run`); copy and replace storage for production
- **`docs/MAVEN_PUBLISH.md`** — Maven Central one-time setup, GitHub secrets, tag publish, and post-release verification checklist
- **`REST_API.md` versioning policy** — semver alignment with library releases, stable 1.0 scope, minor/major change rules, backend checklist

### Changed

- **`:mock-server`** — uses `:syncforge-server` for contract routes; keeps `/dev/*` endpoints for conflict demos only
- **`InMemorySyncStore.pull`** — empty `types` query returns all entity types (matches REST_API.md)
- **POM metadata** — `syncforge.pom.url` and SCM URLs point to `github.com/Arsenoal/syncforge`

### Removed

- **`useSqlDelightPersistence()`** — SQLDelight is the Android default since 0.6.0; use `databaseName("…")` for a custom file name
- **`ConflictResolver`**, **`LastWriteWinsResolver`**, and **`ConflictStrategies.fromResolver()`** — use `ConflictPolicy` + `ConflictStrategies` instead
- **`TypedEntitySyncHandler(legacyResolver)`** constructor — use parameterless constructor; configure via `conflicts { }`

### Added

- **`consumer-smoke/android-minimal`** — standalone app that compiles from `mavenLocal()` / Maven Central coordinates only
- **`verifyConsumerSmoke`** Gradle task — `publishAllToMavenLocal` + consumer compile; runs in CI
- **`syncforge-android-deps` Maven publish fix** — `release` publication registered for local/Central publish
- **Published consumer docs** in `GETTING_STARTED.md` (Android plugin + BOM + `syncforge`; iOS/desktop single-line deps)
- **`databaseName()`** on `SyncForge.android` — stable replacement for removed `useSqlDelightPersistence()`
- **Token refresh on 401** — `RefreshingSyncAuthProvider` + single retry in `KtorSyncTransport`; `SyncError.httpStatus` for status-aware handling
- **iOS background sync** — `IosBackgroundSyncWorkScheduler` (BGAppRefreshTask / BGTaskScheduler), `registerIosBackgroundSyncTasks()`, `SyncForge.ios { schedulePeriodicSyncOnStart() }`
- **`RoomToSqlDelightMigrator` hardening** — batched inserts, `Log` diagnostics, `Status`/`FAILED` result, safe retry after partial failure
- **Migrator tests** — 250-row outbox batch migration and partial-failure recovery coverage
- **Multi-entity E2E tests** — `MultiEntityE2ETest` (task + note flows, conflict isolation) via `./gradlew androidE2e`
- **CI `android-e2e` job** — GitHub Actions emulator + `:mock-server` runs connected instrumented tests
- **iOS multi-entity UI tests** — `MultiEntityUITests` (XCUITest) via `./gradlew iosE2e`
- **CI `ios-e2e` job** — GitHub Actions `macos-14` Simulator + `:mock-server` runs XCUITest smoke tests
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

- `SyncTransport` — pluggable push/pull boundary (default `KtorSyncTransport`, or custom wire formats)
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