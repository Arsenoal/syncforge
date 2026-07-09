# SyncForge roadmap

**Maven Central:** `1.1.0` (stable — [studio.syncforge](https://central.sonatype.com/namespace/studio.syncforge), tag `v1.1.0`)

**Monorepo (`main`):** `1.2.0` development line — 1.2–1.6 features shipped on `main`; git tags only until **2.0.0** Maven Central publish (see [distribution policy](ROADMAP_1_0_TO_2_0.md#distribution-notes-10--20)).

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

## What the library can do today (`main`, 1.2–1.6)

### Core sync (stable since 1.0)

- **Outbox → push → pull** with retry/backoff, network reconnect, pull pagination, and `SyncStatus` surfacing
- **`conflicts { }`** — per-entity LWW, merge, defer-to-user, alwaysLocal/Remote, **`gitLike { }`** three-way merge, experimental **`crdt { }`**
- **`ConflictStrategyKind` catalog** — static and runtime policy updates (`updateConflictPolicy()`)
- **KSP codegen** — `@SyncForgeEntity` + `@SyncForgeDao` → handlers; **`EntityStore`** + `@SyncForgeStore` adapters (`:syncforge-store-*`)
- **Platform DSLs (stable)** — `SyncForge.android { }`, `SyncForge.ios { }`, `SyncForge.desktop { }`, `SyncForge.macos { }`
- **Experimental browser DSL** — `SyncForge.web { }` (Kotlin/JS; monorepo-only — [WEB_SETUP.md](WEB_SETUP.md))

### Transports & backends

- **REST** — injectable `SyncHttpClient` + `RestSyncTransport` (`:syncforge-network-ktor`)
- **BaaS** — `SyncDeltaStore` + `DeltaStoreSyncTransport`; Supabase, Firebase, GraphQL optional modules
- **Reference servers** — Ktor `:mock-server`, Spring `:backend-starter-spring`, GraphQL `:backend-starter-graphql`

### Developer experience

- **Version catalog** — `studio.syncforge:syncforge-catalog` pins all library artifacts + Android plugin
- **DI helpers** — `:syncforge-integration-koin`, `:syncforge-integration-hilt`
- **Debug & ops** — `SyncDebugPanel`, CMP conflict UI, SyncHealth dashboard + diagnostic screen, OpenTelemetry tracing hooks, audit export
- **Samples** — `:sample` (Android), `ios-sample/`, `:sample-desktop`, `:sample-web`

### You still provide

- Your entity store (Room DAO, custom `EntityStore`, etc.) and backend matching [REST_API.md](REST_API.md)
- Conflict resolution UI for `deferToUser()` entities (samples demonstrate this)
- `SyncWorkerFactory` for background sync DI on Android

### Not yet included

- **Maven Central publish** for 1.2+ artifacts (gated to **2.0.0**; use `publishToMavenLocal` or composite build until then)
- **Standalone iOS distribution** (Swift Package Manager / XCFramework — gated to 2.0; KMP framework path today)
- **1.6-07** — conflict/debug CMP UI on web (deferred; Wasm path optional)
- **1.3-06** — shake-to-open debug console

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
| **Android SQLDelight default** | ✅ M4 | `SyncForge.android { }` uses SQLDelight only (Room opt-in removed in 1.0) |
| **Darwin Ktor engine** | ✅ Done | iOS HTTP transport via `ktor-client-darwin` |
| **iOS network monitor** | ✅ M3 | `IosNetworkMonitor` + `NetworkMonitorFactory` |
| **iOS persisted cursor** | ✅ M3 | `UserDefaultsSyncCursorStore` |
| **iOS sample (Kotlin)** | ✅ 0.9.0 | `:sample-ios-shared` + `IosSampleController` (tasks + notes + tags) |
| **iOS sample (SwiftUI)** | ✅ 0.9.0 | `ios-sample/SyncForgeTasks.xcodeproj` (TabView: Tasks / Notes / Tags) |
| **Room → SQLDelight migrator** | ✅ M4+ | `RoomToSqlDelightMigrator` — automatic on Android upgrade |
| **JVM desktop** | ✅ M5 | `SyncForge.desktop { }` + SQLDelight + file cursor |
| **macOS native** | ✅ M5 | `macosArm64`/`macosX64` + `SyncForge.macos { }` |
| **SKIE / Swift API** | ✅ M5 | Plugin + Flow/suspend on framework modules |
| **DataStore cursor** | ✅ Android 1.1 | iOS UserDefaults + desktop file until unified KMP cursor |

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
| **Maven Central** | ✅ `1.0.0` — `studio.syncforge` KMP targets, Gradle plugin, version catalog (1.4+) |
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
| `1.1.x` – `1.5.x` | EntityStore, encrypted TokenStore, per-entity conflict strategies + gitLike merge, DI, CRDT, platform parity, GraphQL/Supabase transports, observability |
| `1.6.x` | Optional web add-on — `SyncForge.web { }`, `:sample-web`, `webE2e` CI (monorepo-only; no Maven publish) |
| `2.0.0` | Maven Central for 1.x backlog, optional op-log/CRDT channel, KMP graduation, REST v2 (if needed) |

**Detailed plan from 1.0.0 through 2.0.0:** [ROADMAP_1_0_TO_2_0.md](ROADMAP_1_0_TO_2_0.md)

---

## 1.0.0 status

**GA (July 2026).** First semver-stable release on Maven Central under `studio.syncforge`.

| Milestone | Status |
|-----------|--------|
| API graduation (`@ExperimentalSyncForgeApi` removal on stable surfaces) | ✅ |
| Remove `useRoomPersistence()` | ✅ |
| Docs freeze (`CHANGELOG`, `MODULES`, `GETTING_STARTED`) | ✅ |
| Version bump + `CHANGELOG` `[1.0.0]` | ✅ |
| Publish `1.0.0` to Maven Central | ✅ ([verify run #1](https://github.com/Arsenoal/syncforge/actions/runs/28852404760)) |
| Git tag `v1.0.0` | ✅ |
| 1.0 sign-off matrix | ✅ |

See [ROADMAP_1_0_TO_2_0.md § 1.0.0](ROADMAP_1_0_TO_2_0.md#100--first-stable-release) for post-1.0 milestones (1.1 → 2.0).

---

## 1.1.0 status (Wire-up)

**GA (July 2026).** · **Codename:** *Wire-up*

| Track | Status |
|-------|--------|
| **Network** — `SyncHttpClient`, `RestSyncTransport`, `httpClient { }` | ✅ |
| **Entity store** — `EntityStore`, `@SyncForgeStore`, `:syncforge-store-*` | ✅ |
| **Security & auth** — encrypted `TokenStore`, `CharArray` login/register, stable auth API | ✅ |
| **DX** — DI recipes (Koin/Hilt), DataStore cursor, BYO-store docs | ✅ |
| **Release gate** — version catalog + acceptance matrix, tag `v1.1.0` | ✅ |
| **Maven Central** | ✅ `1.1.0` — 14 artifacts + consumer smoke verified |

Full breakdown: [ROADMAP_1_0_TO_2_0.md § 1.1.0](ROADMAP_1_0_TO_2_0.md#110-github-issues-breakdown) · sign-off: [§ 1.1.0 acceptance](ROADMAP_1_0_TO_2_0.md#110-sign-off-checklist).

---

## 1.2.x – 1.5.x status (monorepo)

**On `main` (unreleased on Maven Central).** See [CHANGELOG.md](../CHANGELOG.md) `[Unreleased]` for the full list.

| Version | Codename | Headline | Status |
|---------|----------|----------|--------|
| **1.2.0** | *Merge-smart* | `gitLike { }`, CRDT primitives, strategy catalog, runtime policy | ✅ on `main` |
| **1.3.0** | *Everywhere* | `:sample-desktop`, stable iOS/desktop DSLs, CMP conflict UI | ✅ on `main` (SPM deferred to 2.0) |
| **1.4.0** | *Ecosystem* | Spring/GraphQL/Supabase/Firebase transports, version catalog, multi-device E2E | ✅ on `main` |
| **1.5.0** | *Operate* | Tracing, SyncHealth dashboard, hierarchical recipes, rate limiting, audit export | ✅ on `main` |

---

## 1.6.0 status (Web add-on)

**Monorepo GA-ready** · **Codename:** *Web add-on*

| Track | Status |
|-------|--------|
| **Spike + targets** — `js` primary path, `webMain`, `verifyWebSpike` / `verifyWebCompile` | ✅ |
| **`SyncForge.web { }`** — persistence, cursor, Ktor JS transport | ✅ experimental |
| **`:sample-web`** — push + pull against `:mock-server` | ✅ |
| **Docs** — `WEB_SETUP.md`, `WEB_DSL.md`, MODULES stability row | ✅ |
| **CI** — `webE2e` nightly ([`web-e2e.yml`](../.github/workflows/web-e2e.yml)) | ✅ |
| **Distribution** — monorepo / `publishToMavenLocal` only; no Maven Central for `js` | ✅ by design |
| **Git tag `v1.6.0`** | ⬜ pending |

Sign-off: [ROADMAP_1_0_TO_2_0.md § 1.6.0](ROADMAP_1_0_TO_2_0.md#160-sign-off-checklist).

## Next: 2.0.0 (*Converge*)

First Maven Central publish for the 1.x backlog, stable KMP graduation, optional CRDT/op-log channel — see [ROADMAP_1_0_TO_2_0.md § 2.0.0 vision](ROADMAP_1_0_TO_2_0.md#200--major-release-vision) and [§ 2.0.0 sign-off checklist](ROADMAP_1_0_TO_2_0.md#200-sign-off-checklist).

Optional before 2.0: **1.6-07** (web conflict/debug CMP UI) or **1.3-06** (shake-to-open debug).