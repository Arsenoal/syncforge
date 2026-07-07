# SyncForge roadmap: 1.0.0 → 2.0.0

**Baseline:** `0.9.0-rc.5` (published on Maven Central; 1.0 soak in progress)  
**Document date:** July 2026  
**Scope:** Everything from the first stable public release through the next major version.

For pre-1.0 history and completed phases, see [ROADMAP.md](ROADMAP.md).

---

## Executive summary

SyncForge 1.0 establishes a **semver-stable Android + common sync contract**: outbox → push → pull → configurable conflict strategies. Versions **1.1–1.5** deepen developer experience, conflict tooling, platform parity, ecosystem adapters, and observability — without changing the core sync loop. **2.0** is reserved for **opt-in architectural extensions** (field-level CRDT strategies, optional op-log sync mode, KMP platform graduation) that may introduce breaking API or REST contract changes.

```
1.0.0  Stable ship     — API freeze, Maven Central 1.0, remove pre-1.0 deprecations
1.1.x  Integration DX  — DI recipes/modules, auth graduation, persistence polish
1.2.x  Smart conflicts — CRDT primitives, crdt { } strategy, KSP field-merge
1.3.x  Platform parity — Desktop sample, iOS SPM/XCFramework, CMP debug UI
1.4.x  Ecosystem       — Supabase/Spring starters, multi-device E2E, version catalog
1.5.x  Production ops — OpenTelemetry, SyncHealth dashboard, hierarchical sync recipes
2.0.0  Major evolution — Optional CRDT/op-log channel, stable KMP DSLs, REST v2 (if needed)
```

---

## Strategic themes (1.0 → 2.0)

| Theme                     | 1.0                     | 1.x                                    | 2.0                                          |
|---------------------------|-------------------------|----------------------------------------|----------------------------------------------|
| **Core sync loop**        | Stable                  | Hardening only                         | Optional second mode (op-log / CRDT doc)     |
| **Conflict resolution**   | LWW, merge, deferToUser | CRDT helpers + KSP codegen             | `crdt { }` first-class; tombstone-aware sets |
| **Android**               | Primary stable target   | DI modules, ProGuard sign-off          | Room opt-in removed in 0.9.0-rc.5            |
| **iOS / desktop / macOS** | Experimental DSLs       | Sample parity, SPM binary              | Graduate to stable                           |
| **Backend contract**      | REST v1 frozen          | Adapters (Supabase, Spring)            | REST v2 only if op-log needs it              |
| **Distribution**          | BOM + Gradle plugin     | Version catalog, integration artifacts | SPM + Maven parity                           |

---

## Version timeline

| Version   | Codename      | Target window | Headline                                               |
|-----------|---------------|---------------|--------------------------------------------------------|
| **1.0.0** | *Stable*      | Q3 2026       | First semver-stable release                            |
| **1.1.0** | *Wire-up*     | Q4 2026       | DI integration, auth stable, DataStore cursor          |
| **1.2.0** | *Merge-smart* | Q1 2027       | CRDT primitives + `crdt { }` strategy (experimental)   |
| **1.3.0** | *Everywhere*  | Q2 2027       | Desktop sample, iOS SPM, CMP conflict UI               |
| **1.4.0** | *Ecosystem*   | Q3 2027       | Backend starters, Supabase transport, multi-device E2E |
| **1.5.0** | *Operate*     | Q4 2027       | Tracing, metrics dashboard, hierarchical recipes       |
| **2.0.0** | *Converge*    | 2028          | Major API + optional sync modes                        |

Windows are indicative for a small team or part-time maintenance.

---

## 1.0.0 — First stable release

### Goal

Ship a **trustworthy 1.0**: documented, tested, Maven Central, semver guarantees on the Android-primary API. Experimental markers remain only where platform maturity warrants it (iOS/desktop auth, debug surfaces).

### Already complete (from 0.6 → 0.9.0-rc.5)

| Area                 | Delivered                                                                                        |
|----------------------|--------------------------------------------------------------------------------------------------|
| **Sample App Proof** | `:sample` — tasks + notes + tags, multi-screen, per-entity `conflicts { }`, shared `SyncManager` |
| **iOS parity**       | `:sample-ios-shared` + `ios-sample/` SwiftUI TabView, `MultiEntityUITests`                       |
| **Persistence**      | SQLDelight default on Android; `RoomToSqlDelightMigrator` + sign-off tests                       |
| **Network**          | `RefreshingSyncAuthProvider`, 401 retry in `KtorSyncTransport`                                   |
| **Background sync**  | WorkManager (Android), `IosBackgroundSyncWorkScheduler`                                          |
| **Ecosystem**        | `:syncforge-server`, `:backend-starter`, `:mock-server`, `consumer-smoke`                        |
| **Distribution**     | Maven Central BOM, `studio.syncforge.android` Gradle plugin, Apache 2.0                          |
| **CI**               | `androidE2e`, `iosE2e`, `verifyReleaseSignOff`, `verifyConsumerSmoke`                            |
| **API cleanup**      | `ConflictResolver` family removed; `useSqlDelightPersistence()` and `useRoomPersistence()` removed |
| **API graduation**   | Stable Android DSL, core `SyncManager`, `ConflictPolicy`, Compose status + conflict UI (`StableApiSurfaceTest`) |
| **Docs**             | GETTING_STARTED, MODULES stability table, AUTH_API, MAVEN_PUBLISH, REST versioning policy        |
| **Docs freeze**      | `CHANGELOG`, `MODULES`, `GETTING_STARTED` match 1.0 APIs (P0-06, July 2026)                      |
| **Integration tests**| `SyncEngineIntegrationTest` — retry exhaustion, multi-page pull, offline queue (P1-04)           |

### Remaining P0 (1.0 blockers)

| ID        | Job                                                                                                                                                                                                                                                           | Area    |
|-----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|
| 1.0-P0-01 | **API graduation** — remove `@ExperimentalSyncForgeApi` from `SyncForge.android`, core `SyncManager` (`status`, `conflicts`, `sync`/`push`/`pull`, `enqueueChange`, `resolveConflict`), `ConflictPolicy` / `ConflictStrategies`, Compose status + conflict UI | API ✅  |
| 1.0-P0-02 | **Remove `useRoomPersistence()`** — legacy Room opt-in deleted (migration path documented in upgrade guide)                                                                                                                                                   | Android ✅ |
| 1.0-P0-03 | **Publish `1.0.0` to Maven Central** — all artifacts: `syncforge`, `annotations`, `ksp`, `persistence`, `bom`, `android-deps`, Gradle plugin                                                                                                                  | Dist    |
| 1.0-P0-04 | **1.0 sign-off matrix** — run full acceptance checklist (Section 8); tag `v1.0.0`                                                                                                                                                                             | QA      |
| 1.0-P0-05 | **macOS tag publish** — iOS/macOS frameworks from CI without manual steps                                                                                                                                                                                     | CI      |
| 1.0-P0-06 | **Docs freeze** — CHANGELOG, MODULES, GETTING_STARTED match 1.0 APIs exactly                                                                                                                                                                                  | Docs ✅ |

### P1 (strongly recommended for 1.0.0; may slip to 1.0.1)

| ID        | Job                                                                   | Area      |
|-----------|-----------------------------------------------------------------------|-----------|
| 1.0-P1-01 | Upgrade guide: pre-0.6 Room → 0.6+ SQLDelight                         | Docs      |
| 1.0-P1-02 | Conflict recipes for 3+ entity types in RECIPES.md                    | Docs      |
| 1.0-P1-03 | Consumer ProGuard/R8 rules documented + tested (`consumer-rules.pro`) | Android   |
| 1.0-P1-04 | Integration tests: retry exhaustion, multi-page pull, offline queue   | QA ✅     |
| 1.0-P1-05 | Performance test: 1000+ outbox entries, batch push                    | QA        |
| 1.0-P1-06 | Security doc pass: TLS, token storage, no secrets in logs             | Docs      |
| 1.0-P1-07 | At least one external dogfood or documented third-party integration   | Community |

### Explicitly post-1.0 (do not block 1.0)

- Desktop sample app (`:sample-desktop`)
- DataStore KMP cursor
- CRDT / DI integration artifacts
- Supabase / Spring starters
- iOS SPM binary distribution
- Shake-to-open debug console

### 1.0.0 API stability contract

**Stable at 1.0 (no opt-in required):**

- `SyncForge.android { }`, `SyncForgeAndroid.workManagerConfiguration`
- `SyncManager` — sync lifecycle, outbox enqueue, conflict resolution, scheduling hooks
- `ConflictPolicy`, `conflicts { }`, `ConflictChoice`, `resolveConflict()`
- Compose production UI — `SyncStatusUiModel`, `collectSyncStatusUiModel()`, conflict chip/sheet
- `databaseName()`, KSP-generated handlers

**Experimental at 1.0 (may change in 1.x minors):**

- `SyncForge.ios { }`, `SyncForge.desktop { }`, `SyncForge.macos { }`
- `SyncForge.create()` / `SyncForgeBuilder` (low-level factory)
- Built-in `auth { }` DSL and `SyncManager.register`/`login`/`logout`
- `SyncManager.debug`, `conflictHistory`, `SyncDebug*`
- `SyncForgePersistence` custom wiring extensions

---

## 1.1.x — Integration & persistence polish

### Goal

Reduce boilerplate for real apps: **dependency injection**, smoother auth, unified cursor storage. Core sync semantics unchanged.

### Features

| ID     | Job                                                                                      | Priority | Notes                                                                           |
|--------|------------------------------------------------------------------------------------------|----------|---------------------------------------------------------------------------------|
| 1.1-01 | **DI recipes** — Koin + Hilt copy-paste modules in RECIPES.md                            | P0       | No core DI dependency                                                           |
| 1.1-02 | **`syncforge-integration-koin`** optional artifact                                       | P1       | `syncForgeModule(context) { configure }`, `SyncManagerProvider` for WorkManager |
| 1.1-03 | **`syncforge-integration-hilt`** optional artifact                                       | P1       | `@Provides` templates, WorkManager `Configuration` helper                       |
| 1.1-04 | **Graduate built-in auth** — `auth { }`, `authState`, `register`/`login`/`logout` stable | P1       | After 1.0 soak feedback                                                         |
| 1.1-05 | **DataStore Preferences cursor (KMP)**                                                   | P1       | Replace SharedPreferences / UserDefaults for pull cursor                        |
| 1.1-06 | **Sample: Hilt or Koin variant**                                                         | P2       | Optional `:sample-di` or documented fork                                        |
| 1.1-07 | **`SyncForgeBuilder` graduation**                                                        | P2       | Stable low-level factory for custom transports                                  |
| 1.1-08 | **Patch: 1.0.x bugfix lane**                                                             | P0       | Semver patches only — no new APIs                                               |

### DI architecture (1.1)

```
:syncforge                    ← no Koin/Dagger dependency (unchanged)
:syncforge-integration-koin   ← optional, depends on koin-core
:syncforge-integration-hilt   ← optional, Android-only
```

App always supplies: `baseUrl`, `EntityRegistry`/DAOs, `conflicts { }`. Library supplies factory helpers only.

### 1.1.0 acceptance criteria

- [ ] RECIPES.md DI section with working Koin + Hilt examples matching `:sample`
- [ ] DataStore cursor on Android; file/UserDefaults fallback documented for iOS until unified
- [ ] BOM lists optional integration artifacts (not transitive)
- [ ] No breaking changes to 1.0 stable APIs

---

## 1.2.x — Conflict evolution (CRDT as strategy)

### Goal

Reduce hand-written `merge { }` code and auto-resolve more pull conflicts for mergeable fields — **without replacing** outbox → push → pull.

### Features

| ID     | Job                                                                            | Priority | Notes                                          |
|--------|--------------------------------------------------------------------------------|----------|------------------------------------------------|
| 1.2-01 | **CRDT primitives** — `LwwRegister<T>`, `OrSet<T>`, `GCounter` in `commonMain` | P0       | Serializable; usable inside `merge { }`        |
| 1.2-02 | **`crdt { }` conflict strategy** (experimental)                                | P0       | Per-field CRDT config in `conflicts { }`       |
| 1.2-03 | **KSP field-merge annotations** — `@Lww`, `@OrSet`, `@GCounter`                | P1       | Generates merge logic into handlers            |
| 1.2-04 | **Tombstone-aware merge recipes**                                              | P1       | Delete vs update; when to keep `deferToUser()` |
| 1.2-05 | **Multi-device E2E (single emulator concurrent edit)**                         | P1       | Validates LWW + merge + defer paths            |
| 1.2-06 | **CONFLICT_RESOLUTION.md v2**                                                  | P1       | Decision tree: LWW vs merge vs crdt vs defer   |

### Conflict strategy matrix (target 1.2)

| Entity pattern           | Recommended strategy                     |
|--------------------------|------------------------------------------|
| Simple row (settings)    | `lastWriteWins()` or `alwaysRemote()`    |
| Multi-field edits        | `merge { }` or `crdt { }`                |
| Tags, collaborators      | `crdt { field("tags") { orSet() } }`     |
| Counters                 | `crdt { field("views") { gCounter() } }` |
| Delete semantics / legal | `deferToUser()`                          |
| Tasks (sample)           | `deferToUser()` — unchanged              |

### 1.2.0 acceptance criteria

- [ ] `CrdtMergeStrategy` implements `ConflictStrategy`; wired through `ConflictPullApplier`
- [ ] Unit tests per CRDT primitive + integration test for concurrent tag merge on pull
- [ ] `crdt { }` marked `@ExperimentalSyncForgeApi` until 2.0 graduation
- [ ] Delete-conflict E2E still passes with `deferToUser()` on tasks

---

## 1.3.x — Platform parity & distribution

### Goal

iOS and desktop are **first-class documented paths**, not compile-only targets. Improve distribution for non-Gradle consumers.

### Features

| ID     | Job                                                                           | Priority | Notes                                       |
|--------|-------------------------------------------------------------------------------|----------|---------------------------------------------|
| 1.3-01 | **`:sample-desktop`** — minimal CLI or Compose Multiplatform                  | P0       | Proves `SyncForge.desktop { }`              |
| 1.3-02 | **Graduate iOS DSL** — `SyncForge.ios { }` stable after desktop + device soak | P1       | Requires 1.1 cursor + auth hardening        |
| 1.3-03 | **Graduate desktop/macos DSLs**                                               | P1       | Pair with desktop sample                    |
| 1.3-04 | **Swift Package Manager / XCFramework publish**                               | P1       | CI artifact on tag; IOS_SETUP.md update     |
| 1.3-05 | **Compose Multiplatform conflict/debug UI**                                   | P2       | Share Android conflict sheet on iOS/desktop |
| 1.3-06 | **Shake-to-open `SyncDebugLauncher`**                                         | P2       | Debug builds only                           |
| 1.3-07 | **SKIE Swift API review** — document recommended Swift patterns               | P1       | Flow collection, error handling             |

### 1.3.0 acceptance criteria

- [ ] Desktop sample runs against `:mock-server` with push + pull
- [ ] iOS consumer can integrate via documented SPM or KMP framework path
- [ ] Platform stability table in MODULES.md updated (iOS/desktop → Stable)
- [ ] `iosE2e` + desktop smoke in CI (desktop may be JVM-only nightly)

---

## 1.4.x — Ecosystem & advanced QA

### Goal

Meet teams where their backend already lives; prove concurrent multi-device behavior.

### Features

| ID     | Job                                                   | Priority | Notes                                              |
|--------|-------------------------------------------------------|----------|----------------------------------------------------|
| 1.4-01 | **Spring Boot backend starter**                       | P0       | Uses `:syncforge-server` routes; JDBC store option |
| 1.4-02 | **Supabase transport adapter** (experimental)         | P1       | Optional; maps push/pull to Supabase patterns      |
| 1.4-03 | **Gradle version catalog for consumers**              | P1       | Published alongside BOM                            |
| 1.4-04 | **Multi-device E2E** — two emulators, concurrent edit | P1       | Conflict + CRDT validation                         |
| 1.4-05 | **Backend contract test kit**                         | P2       | Shared test harness for custom servers             |
| 1.4-06 | **Firebase / custom webhook transport spike**         | P3       | Evaluate demand before committing                  |

### 1.4.0 acceptance criteria

- [ ] Spring starter documented with docker-compose quickstart
- [ ] Version catalog published to Maven Central
- [ ] Multi-device E2E green in nightly CI
- [ ] REST_API.md documents adapter expectations (transport implements same contract)

---

## 1.5.x — Observability & production hardening

### Goal

Operators and senior developers can **see** sync health in production-like environments.

### Features

| ID     | Job                                  | Priority | Notes                                                       |
|--------|--------------------------------------|----------|-------------------------------------------------------------|
| 1.5-01 | **Structured tracing hooks**         | P0       | OpenTelemetry-compatible spans: push, pull, conflict, retry |
| 1.5-02 | **SyncHealth metrics expansion**     | P1       | Latency percentiles, conflict rate, outbox depth            |
| 1.5-03 | **Full SyncHealth dashboard UI**     | P1       | Debug + optional release diagnostic screen                  |
| 1.5-04 | **Hierarchical sync recipes**        | P1       | Parent/child entities, orphan FK guidance                   |
| 1.5-05 | **Rate limiting + backoff policies** | P2       | Server-friendly client behavior                             |
| 1.5-06 | **Audit log export**                 | P2       | Conflict history CSV/JSON for support                       |

### 1.5.0 acceptance criteria

- [ ] Tracing opt-in does not allocate when disabled
- [ ] Dashboard shows outbox, last sync, conflict count, error breakdown
- [ ] BEST_PRACTICES.md hierarchical section with explicit limitations

---

## 2.0.0 — Major release vision

### Goal

Optional **second sync mode** for CRDT-heavy or real-time products, while keeping REST entity sync as the default. Graduate experimental 1.x APIs or remove them.

### Candidate 2.0 themes

| Theme                                       | Description                                                             | Breaking?                               |
|---------------------------------------------|-------------------------------------------------------------------------|-----------------------------------------|
| **Stable KMP everywhere**                   | All platform DSLs stable; single stability table                        | Maybe — experimental removals           |
| **`crdt { }` stable**                       | Field CRDT strategy graduates from experimental                         | No if already shipped in 1.2            |
| **Optional op-log / CRDT document channel** | Separate sync path for collaborative docs (alongside entity push/pull)  | Yes — new module e.g. `:syncforge-crdt` |
| **REST API v2**                             | Only if op-log requires new endpoints; v1 remains supported             | Yes — server + client major             |
| **Remove legacy**                           | `useRoomPersistence` gone since 1.0; clean deprecated auth/debug shapes | Yes                                     |
| **Plugin-generated DI modules**             | KSP emits Koin/Hilt module stubs from `@SyncForgeEntity`                | No — additive                           |

### 2.0 explicit non-goals

- Replacing Room/SQLDelight as the app’s entity store
- Full real-time WebSocket sync as the only mode
- Automatic CRDT for whole JSON blobs without schema
- Bundling Koin or Dagger into core `:syncforge`

### 2.0 architecture sketch

```
                    ┌─────────────────────────────────────┐
                    │           App entities              │
                    │     (Room / SQLDelight / custom)    │
                    └─────────────────┬───────────────────┘
                                      │
              ┌───────────────────────┴───────────────────────┐
              │                                               │
    ┌─────────▼──────────┐                         ┌───────────▼──────────┐
    │  Entity sync (1.x) │                         │ Op-log / CRDT (2.0)  │
    │  outbox→push→pull  │                         │ optional :syncforge- │
    │  ConflictPolicy    │                         │ crdt module          │
    └─────────┬──────────┘                         └───────────┬──────────┘
              │                                               │
              └───────────────────────┬───────────────────────┘
                                      │
                            ┌─────────▼─────────┐
                            │  SyncTransport(s) │
                            │  REST v1 (+ v2?)  │
                            └───────────────────┘
```

### 2.0.0 acceptance criteria

- [ ] Migration guide 1.5 → 2.0 with codemods or deprecation timeline
- [ ] Entity sync mode unchanged for existing consumers (opt-in for new mode)
- [ ] All 1.x experimental APIs either stable or removed with replacement
- [ ] REST v1 supported for minimum 12 months after v2 introduction (if v2 ships)
- [ ] Full CI matrix: Android E2E, iOS E2E, desktop smoke, consumer-smoke, multi-device

---

## REST API evolution

| Library version | REST contract             | Notes                                                            |
|-----------------|---------------------------|------------------------------------------------------------------|
| 1.0.x           | **v1 frozen**             | `POST /sync/push`, `GET /sync/pull` per REST_API.md              |
| 1.x             | v1 + optional auth routes | `/auth/*` stable with built-in auth                              |
| 2.0             | v1 + optional **v2**      | v2 only if op-log/CRDT channel needs it; document in REST_API.md |

Backend implementers should pin to a library major version in their compatibility matrix.

---

## Job priority legend

| Priority | Meaning                                  |
|----------|------------------------------------------|
| **P0**   | Required for that version’s release      |
| **P1**   | Strongly recommended; may slip one patch |
| **P2**   | Nice to have; user-driven prioritization |
| **P3**   | Spike / evaluate                         |

---

## Risk register (1.0 → 2.0)

| Risk                                  | Severity | Mitigation                                                                     |
|---------------------------------------|----------|--------------------------------------------------------------------------------|
| 1.0 API surface too large             | Medium   | Stable = Android + common contracts; KMP stays experimental until 1.3          |
| CRDT gives wrong merges for deletes   | High     | Keep `deferToUser()`; document tombstone patterns; never default CRDT globally |
| DI integration artifact fragmentation | Low      | Max two optional modules; recipes first in 1.1.0                               |
| iOS SPM publish complexity            | Medium   | Ship XCFramework from existing macOS CI job; fallback to KMP framework         |
| REST v2 splits ecosystem              | High     | Defer to 2.0; v1 long support window; adapters implement v1 only in 1.x        |
| Multi-device E2E flakiness            | Medium   | Nightly only initially; mock-server health gate                                |
| Scope creep into “full backend”       | Medium   | Starters are reference kits; `:syncforge-server` stays minimal                 |

---

## Indicative timeline (from 1.0.0 GA)

Assuming part-time maintenance or a small team:

| Phase           | Duration   | Cumulative |
|-----------------|------------|------------|
| 1.0.0 soak → GA | 2–4 weeks  | ~1 month   |
| 1.1.x           | 6–8 weeks  | ~3 months  |
| 1.2.x           | 8–10 weeks | ~5 months  |
| 1.3.x           | 8–10 weeks | ~8 months  |
| 1.4.x           | 6–8 weeks  | ~10 months |
| 1.5.x           | 6–8 weeks  | ~12 months |
| 2.0.0 RC + GA   | 8–12 weeks | ~15 months |

Adjust based on contributor capacity and dogfood feedback after 1.0.

---

## Acceptance criteria matrices

### 1.0.0 sign-off checklist

1. All 1.0-P0 jobs completed and verified.
2. Sample App Proof: two+ entities, two+ DAOs, two+ screens, shared `SyncManager` — E2E green.
3. No `@ExperimentalSyncForgeApi` on `SyncForge.android`, core `SyncManager`, `ConflictPolicy`, Compose status UI.
4. Deprecated APIs removed (`ConflictResolver` family, `useSqlDelightPersistence`, `useRoomPersistence`).
5. Maven Central: `syncforge`, `annotations`, `ksp`, `persistence`, `bom`, `android-deps`, Gradle plugin — all at `1.0.0`.
6. CI green: compile (Android+JVM), `jvmTest`, `testDebugUnitTest`, `androidE2e` (nightly minimum), `iosE2e` on macOS runner.
7. macOS tag publish produces iOS/macOS frameworks without manual steps.
8. CHANGELOG, MODULES, GETTING_STARTED reflect 1.0 APIs accurately.
9. Migration tested: 0.4 Room → 0.6 SQLDelight → 1.0 on sample upgrade path.
10. At least one external dogfood or documented third-party integration attempt.

### 2.0.0 sign-off checklist

1. All P0 jobs for 1.5–2.0 completed.
2. Entity sync (1.x path) works unchanged for consumers who do not opt into new modules.
3. Platform DSL stability: Android, iOS, desktop, macOS — all stable or documented exceptions.
4. Conflict: `crdt { }` graduated or explicitly remains experimental with 2.x plan.
5. DI: integration artifacts published and referenced in GETTING_STARTED.
6. Ecosystem: Spring starter + at least one hosted adapter documented.
7. Observability: tracing hooks documented with sample exporter.
8. REST: versioning policy updated; v1 deprecation timeline published if v2 exists.
9. Full upgrade guide 1.x → 2.0 with breaking change enumeration.
10. Security review pass on auth, token storage, and transport defaults.

---

## How to use this document

- **Release planning** — pull P0 jobs into GitHub milestones (`1.0.0`, `1.1.0`, …).
- **Issue templates** — reference job IDs (e.g. `1.2-03`) in feature requests.
- **PR scope** — a PR should map to one version band unless it is a patch fix.
- **Community** — vote on P2/P3 items via GitHub Discussions or issues.

When a version ships, update [CHANGELOG.md](../CHANGELOG.md), mark jobs done here, and refresh the summary table at the top.

---

## Related documents

| Document                                                                      | Purpose                                   |
|-------------------------------------------------------------------------------|-------------------------------------------|
| [ROADMAP.md](ROADMAP.md)                                                      | Pre-1.0 phases and current status         |
| [MODULES.md](MODULES.md)                                                      | API stability by area                     |
| [REST_API.md](REST_API.md)                                                    | Backend contract + versioning             |
| [CONFLICT_RESOLUTION.md](CONFLICT_RESOLUTION.md)                              | Strategy guide (CRDT section planned 1.2) |
| [SyncForge-1.0-P0.docx](SyncForge-1.0-P0.docx)                                | Short 1.0 blocker checklist (Word)        |
| [SyncForge-Roadmap-1.0-to-2.0.docx](SyncForge-Roadmap-1.0-to-2.0.docx)        | This roadmap as Word                      |
| [scripts/generate-roadmap-docx.py](../scripts/generate-roadmap-docx.py)       | Regenerate both Word roadmaps             |
| [scripts/generate-1.0-roadmap-pdf.py](../scripts/generate-1.0-roadmap-pdf.py) | PDF generator for pre-1.0 roadmap         |