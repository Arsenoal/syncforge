# SyncForge roadmap

**Current version:** `0.9.0-rc.5` (pre-1.0; 1.0 soak in progress)

---

## Completed phases

| Phase | Version | Delivered |
|-------|---------|-----------|
| **1** | 0.0.1 | Architecture skeleton, interfaces, `NoOpSyncTransport`, conflict resolver |
| **2** | 0.1.0 | Room outbox, optimistic writes + rollback, pull delta application |
| **3** | 0.2.0 | `KtorSyncTransport`, mock server, sample app, persisted cursor |
| **4** | 0.3.0 | Retry/backoff, network reconnect sync, pull pagination, KSP codegen, WorkManager sample |
| **5** | 0.4.0 | `SyncForge.android` DSL, conflict strategies + Room store, sync debug console, conflict Compose UI |
| **6** | 0.6.0 | SQLDelight Android default, Room → SQLDelight migrator, legacy Room opt-in deprecated, E2E tests |

---

## What the library can do today (v0.6.0)

### Production-oriented features

- **Exponential backoff retry** — failed pushes retry up to `maxRetries` without rolling back transient network errors
- **Permanent failure surfacing** — validation/auth errors and exhausted retries appear in `SyncStatus`
- **Network reconnect sync** — auto-push when connectivity returns
- **Offline status** — `SyncStatus.Offline` when queued but no network
- **Pull pagination** — large delta sets fetched in pages via `pullPageSize`
- **HTTP error mapping** — Ktor transport maps status codes to `SyncError.Code`
- **Bearer auth** — `SyncAuthProvider` on `KtorSyncTransport`
- **KSP handler generation** — `@SyncForgeEntity` + `@SyncForgeDao` → `*SyncHandler` + `SyncForgeHandlers`
- **WorkManager** — periodic sync + one-off retry scheduling in sample app

### Developer experience (v0.4.0+)

- **`SyncForge.android { }`** — ~10-line Application setup with Android defaults
- **`SyncForgeBuilder`** — auto-derives entity types from handlers; no duplicated `SyncConfig`
- **`conflicts { }` block** — per-entity strategies: LWW, merge, defer-to-user
- **SQLDelight conflict store** — open conflicts persist across process death (all platforms)
- **`SyncManager.resolveConflict()`** — user-driven resolution for deferred conflicts
- **In-app debug console** — `SyncDebugLauncher` + `SyncDebugPanel` for outbox, health, events, conflicts
- **Compose conflict UI** — `SyncConflictChip`, `SyncConflictResolutionSheet`

### You still provide

- Room schema + DAOs (handlers can be KSP-generated)
- Backend matching [REST_API.md](REST_API.md)
- Conflict resolution UI for `deferToUser()` entities (sample demonstrates this)
- `SyncWorkerFactory` for background sync DI (sample wires via `SyncForgeAndroid.workManagerConfiguration`)

### Kotlin Multiplatform (M1–M5)

- **`SyncForge.ios { }`** — SQLDelight outbox/conflicts, UserDefaults cursor, NWPathMonitor
- **`SyncForge.desktop { }`** — JVM desktop with SQLDelight, `FileSyncCursorStore`, OkHttp transport
- **`SyncForge.macos { }`** — native macOS (`macosArm64` / `macosX64`) with iOS-parity defaults
- **SQLDelight on Android** — default since 0.6.0; legacy `useRoomPersistence()` removed in 0.9.0-rc.5
- **SKIE** — improved Swift interop (`Flow`, suspend) on `:syncforge` and `:sample-ios-shared`
- **`ios-sample/`** — SwiftUI Xcode reference app (tasks + notes + tags; see [iOS sample parity](#ios-sample-parity-090-))

### Not yet included

- Packaged desktop sample app (`:sample-desktop`)
- Standalone iOS distribution (Swift Package Manager / XCFramework — iOS still via KMP today)
- Shake-to-open debug console
- Full sync health metrics dashboard (basic `SyncHealth` exists today)

---

## Phase 6 — Kotlin Multiplatform expansion

**Target version:** `0.6.0` · **Persistence cutover complete** — see [KMP_MIGRATION.md](KMP_MIGRATION.md)

### M1 complete (v0.4.x groundwork)

- `iosMain` source set with `iosX64`, `iosArm64`, `iosSimulatorArm64` targets
- `KtorSyncTransport` moved to `commonMain` with `expect/actual` HTTP engines
- `SyncForge.ios { }` bootstrap DSL (Darwin transport)
- `InMemoryConflictStore` promoted to `commonMain`

### M5 complete (v0.5.x desktop + SKIE)

- `SyncForge.desktop { }` — JVM desktop DSL with SQLDelight + file cursor
- `SyncForge.macos { }` — native macOS targets (`macosArm64` / `macosX64`)
- SKIE on `:syncforge` and `:sample-ios-shared` for Swift `Flow` / suspend interop
- [DESKTOP_SETUP.md](DESKTOP_SETUP.md)

### M2–M4 complete (v0.5.0-rc groundwork)

- `:syncforge-persistence` — SQLDelight schemas + platform drivers (no `:syncforge` dependency)
- `:syncforge` `syncPersistenceMain` — `SqlDelightOutboxRepository`, `SqlDelightConflictStore`
- `SyncForge.ios { }` — SQLDelight outbox + conflicts by default
- SQLDelight Android default + `RoomToSqlDelightMigrator` — shipped in 0.6.0

| Item | Status | Description |
|------|--------|-------------|
| **iOS target** | ✅ M3 | Framework compiles; `SyncForge.ios { }` with SQLDelight, UserDefaults cursor, NWPathMonitor |
| **SQLDelight / multiplatform storage** | ✅ M4 | Default on all platforms since 0.6.0 |
| **Android SQLDelight default** | ✅ M4 | `SyncForge.android { }` uses SQLDelight only (Room opt-in removed 0.9.0-rc.5) |
| **Darwin Ktor engine** | ✅ Done | iOS HTTP transport via `ktor-client-darwin` |
| **iOS network monitor** | ✅ M3 | `IosNetworkMonitor` + `NetworkMonitorFactory` |
| **iOS persisted cursor** | ✅ M3 | `UserDefaultsSyncCursorStore` |
| **iOS sample (Kotlin)** | ✅ 0.9.0 | `:sample-ios-shared` + `IosSampleController` (tasks + notes + tags) |
| **iOS sample (SwiftUI)** | ✅ 0.9.0 | `ios-sample/SyncForgeTasks.xcodeproj` (TabView: Tasks / Notes / Tags) |
| **Room → SQLDelight migrator** | ✅ M4+ | `RoomToSqlDelightMigrator` — automatic on Android upgrade |
| **JVM desktop** | ✅ M5 | `SyncForge.desktop { }` + SQLDelight + file cursor |
| **macOS native** | ✅ M5 | `macosArm64`/`macosX64` + `SyncForge.macos { }` |
| **SKIE / Swift API** | ✅ M5 | Plugin + Flow/suspend on framework modules |
| **DataStore cursor** | ⬜ M5+ | Multiplatform cursor (file/UserDefaults today) |

---

## iOS sample parity (0.9.0) ✅

**Status:** Complete — `ios-sample/` + `:sample-ios-shared` mirror Android `:sample` (tasks + notes + tags).

| Capability | Android `:sample` | iOS (`ios-sample/`) |
|------------|-------------------|---------------------|
| Entity types | tasks, notes, tags | tasks, notes, tags |
| Registry | `SyncForgeHandlers.registry(...)` (KSP) | `EntityRegistry.of(task, note, tag handlers)` |
| Conflict policies | tasks `deferToUser()`, notes/tags LWW | Same |
| UI | Compose bottom nav | SwiftUI `TabView` |
| Verification | `MultiEntityE2ETest` + CI `android-e2e` | `MultiEntityUITests` + CI `ios-e2e` |

**Key paths:** `:sample-ios-shared` (`IosSampleController`), `ios-sample/SyncForgeTasks/` (SwiftUI tabs).

---

## Phase 7 — Distribution & 1.0

**Target version:** `1.0.0`

| Item | Description |
|------|-------------|
| **Maven Central** | ✅ `0.9.0-rc.5` — `studio.syncforge` BOM, KMP targets, Gradle plugin |
| **Open-source license** | Choose and apply before public release |
| **API stability** | Semver guarantees from 1.0 |
| **Backend starter kits** | Reference servers (Ktor, Spring) |
| **401 token refresh** | ✅ `RefreshingSyncAuthProvider` in `KtorSyncTransport` |
| **Supabase adapter** | Optional hosted-backend transport |

---

## Version guide

| Range | Meaning |
|-------|---------|
| `0.0.x` – `0.2.x` | Architecture + demo stack |
| `0.3.x` | Production-oriented hardening |
| `0.4.x` | Developer experience, conflicts, observability |
| `0.5.x` | KMP platform expansion |
| `0.6.x` – `0.9.x` | SQLDelight default, KMP samples, Maven Central RC |
| `1.0.0` | Stable public API + semver guarantees |
| `1.1.x` – `1.5.x` | DI, CRDT strategies, platform parity, ecosystem, observability |
| `2.0.0` | Optional op-log/CRDT channel, KMP graduation, REST v2 (if needed) |

**Detailed plan from 1.0.0 through 2.0.0:** [ROADMAP_1_0_TO_2_0.md](ROADMAP_1_0_TO_2_0.md)

---

## 1.0.0 status (soak from `0.9.0-rc.5`)

Most pre-1.0 P0 work is complete (Sample App Proof, iOS parity, auth refresh, backend starter, E2E CI, Maven Central RC). Remaining before tagging **1.0.0**:

| Blocker | Status |
|---------|--------|
| API graduation (`@ExperimentalSyncForgeApi` removal on stable surfaces) | ✅ |
| Remove `useRoomPersistence()` | ✅ |
| Docs freeze (`CHANGELOG`, `MODULES`, `GETTING_STARTED`) | ✅ |
| Publish `1.0.0` to Maven Central | ⬜ |
| 1.0 sign-off matrix | ⬜ |

See [ROADMAP_1_0_TO_2_0.md § 1.0.0](ROADMAP_1_0_TO_2_0.md#100--first-stable-release) for the full checklist and post-1.0 milestones.