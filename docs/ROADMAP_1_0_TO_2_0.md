# SyncForge roadmap: 1.0.0 → 2.0.0

**Baseline:** `1.0.0` (GA — Maven Central + tag `v1.0.0`, July 2026)
**Document date:** July 2026  
**Scope:** Everything from the first stable public release through the next major version.

For pre-1.0 history and completed phases, see [ROADMAP.md](ROADMAP.md).

---

## Executive summary

SyncForge 1.0 establishes a **semver-stable Android + common sync contract**: outbox → push → pull → configurable conflict strategies. Versions **1.1–1.5** deepen developer experience, conflict tooling, **pluggable app entity stores** (Room, Realm, or any adapter), platform parity, ecosystem adapters, and observability — without changing the core sync loop. **2.0** is reserved for **opt-in architectural extensions** (field-level CRDT strategies, optional op-log sync mode, KMP platform graduation) that may introduce breaking API or REST contract changes.

```
1.0.0  Stable ship     — API freeze, Maven Central 1.0, remove pre-1.0 deprecations
1.1.x  Integration DX  — EntityStore + injectable Ktor client, DI, auth hardening, cursor
1.2.x  Smart conflicts — per-entity strategies, gitLike { }, CRDT, KSP field-merge
1.3.x  Platform parity — Desktop sample, iOS SPM/XCFramework, CMP debug UI
1.4.x  Ecosystem       — Spring/GraphQL/Supabase transports, multi-device E2E, version catalog
1.5.x  Production ops — OpenTelemetry, SyncHealth dashboard, hierarchical sync recipes
1.6.x  Web add-on      — Optional Kotlin/JS or Wasm browser target (`SyncForge.web { }`, `:sample-web`)
2.0.0  Major evolution — Optional CRDT/op-log channel, stable KMP DSLs, REST v2 (if needed)
```

---

## Strategic themes (1.0 → 2.0)

| Theme                     | 1.0                     | 1.x                                    | 2.0                                          |
|---------------------------|-------------------------|----------------------------------------|----------------------------------------------|
| **Core sync loop**        | Stable                  | Hardening only                         | Optional second mode (op-log / CRDT doc)     |
| **Conflict resolution**   | Per-entity `conflicts { }` — LWW, merge, defer, alwaysLocal/Remote | `gitLike { }` three-way merge, strategy catalog, app-selectable per entity | `crdt { }` stable; tombstone-aware sets |
| **App entity store**      | Room-first DX (KSP)     | **`EntityStore` abstraction** + adapters | Any store via handler; Room not required     |
| **Android**               | Primary stable target   | DI modules, ProGuard sign-off          | Legacy Room internals removed at 1.0         |
| **iOS**                   | Experimental DSL        | **Stable DSL (1.3-02)**; SPM binary    | Stable                                       |
| **Desktop / macOS**       | Experimental DSLs       | **Stable DSLs (1.3-03)** + `:sample-desktop` | Stable                                   |
| **Web (browser)**         | Not in scope            | **Optional 1.6 add-on** — `SyncForge.web { }`, `:sample-web` | Stable or documented experimental exception |
| **HTTP client (REST)**    | Ktor bundled in `KtorSyncTransport` | Injectable `HttpClient` + `RestSyncTransport` refactor | User supplies Ktor client (interceptors, engines); SyncForge owns push/pull DTOs |
| **Backend / transport**   | REST v1 frozen (`KtorSyncTransport`); `SyncTransport` plug-in | **`SyncDeltaStore` + `DeltaStoreSyncTransport`** for BaaS; GraphQL/Supabase/Firebase modules | REST v2 only if op-log needs it; wire format pluggable via `SyncTransport` |
| **Distribution**          | BOM + Gradle plugin     | Version catalog, integration artifacts | SPM + Maven parity                           |

---

## Version timeline

| Version   | Codename      | Target window | Headline                                               |
|-----------|---------------|---------------|--------------------------------------------------------|
| **1.0.0** | *Stable*      | Q3 2026       | First semver-stable release                            |
| **1.1.0** | *Wire-up*     | Q4 2026       | EntityStore + HTTP client, DI, encrypted tokens + credential APIs, DataStore cursor |
| **1.2.0** | *Merge-smart* | Q1 2027       | Per-entity strategy catalog, `gitLike { }`, CRDT + KSP field-merge |
| **1.3.0** | *Everywhere*  | Q2 2027       | Desktop sample, iOS SPM, CMP conflict UI               |
| **1.4.0** | *Ecosystem*   | Q3 2027       | Spring + GraphQL transports, Supabase adapter, multi-device E2E |
| **1.5.0** | *Operate*     | Q4 2027       | Tracing, metrics dashboard, hierarchical recipes       |
| **1.6.0** | *Web add-on*  | 2028 (opt.)   | Browser target, `SyncForge.web { }`, `:sample-web`     |
| **2.0.0** | *Converge*    | 2028          | Major API + optional sync modes                        |

Windows are indicative for a small team or part-time maintenance.

---

## 1.0.0 — First stable release

### Goal

Ship a **trustworthy 1.0**: documented, tested, Maven Central, semver guarantees on the Android-primary API. Experimental markers remain only where platform maturity warrants it (iOS/desktop auth, debug surfaces).

### Already complete (from 0.6 → 1.0.0)

| Area                 | Delivered                                                                                        |
|----------------------|--------------------------------------------------------------------------------------------------|
| **Sample App Proof** | `:sample` — tasks + notes + tags, multi-screen, per-entity `conflicts { }`, shared `SyncManager` |
| **iOS parity**       | `:sample-ios-shared` + `ios-sample/` SwiftUI TabView, `MultiEntityUITests`                       |
| **Persistence**      | SQLDelight default on Android; `RoomToSqlDelightMigrator` + sign-off tests                       |
| **Network**          | `RefreshingSyncAuthProvider`, 401 retry in `KtorSyncTransport`                                   |
| **Background sync**  | WorkManager (Android), `IosBackgroundSyncWorkScheduler`                                          |
| **Ecosystem**        | `:syncforge-server`, `:syncforge-transport-core`, `:syncforge-transport-supabase`, `:syncforge-transport-firebase`, `:syncforge-transport-graphql`, `:backend-starter`, `:backend-starter-spring`, `:backend-starter-graphql`, `:mock-server`, `consumer-smoke` |
| **Distribution**     | Maven Central `1.0.0` (BOM, KMP targets, Gradle plugin), tag `v1.0.0`, Apache 2.0                |
| **CI**               | `androidE2e`, `iosE2e`, `verifyReleaseSignOff`, `verifyConsumerSmoke`                            |
| **API cleanup**      | `ConflictResolver` family removed; `useSqlDelightPersistence()` and `useRoomPersistence()` removed |
| **API graduation**   | Stable Android DSL, core `SyncManager`, `ConflictPolicy`, Compose status + conflict UI (`StableApiSurfaceTest`) |
| **Docs**             | GETTING_STARTED, MODULES stability table, AUTH_API, MAVEN_PUBLISH, REST versioning policy        |
| **Docs freeze**      | `CHANGELOG`, `MODULES`, `GETTING_STARTED` match 1.0 APIs (P0-06, July 2026)                      |
| **Integration tests**| `SyncEngineIntegrationTest` — retry exhaustion, multi-page pull, offline queue (P1-04)           |

### P0 checklist (1.0 — complete)

| ID        | Job                                                                                                                                                                                                                                                           | Area       |
|-----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------|
| 1.0-P0-01 | **API graduation** — remove `@ExperimentalSyncForgeApi` from `SyncForge.android`, core `SyncManager` (`status`, `conflicts`, `sync`/`push`/`pull`, `enqueueChange`, `resolveConflict`), `ConflictPolicy` / `ConflictStrategies`, Compose status + conflict UI | API ✅     |
| 1.0-P0-02 | **Remove `useRoomPersistence()`** — legacy Room opt-in deleted (migration path documented in upgrade guide)                                                                                                                                                   | Android ✅ |
| 1.0-P0-03 | **Publish `1.0.0` to Maven Central** — all artifacts: `syncforge`, `annotations`, `ksp`, `persistence`, `bom`, `android-deps`, Gradle plugin                                                                                                                  | Dist ✅    |
| 1.0-P0-04 | **1.0 sign-off matrix** — full acceptance checklist (Section 8); tag `v1.0.0`                                                                                                                                                                                   | QA ✅      |
| 1.0-P0-05 | **macOS publish** — iOS/macOS KMP targets on Central ([publish run #29](https://github.com/Arsenoal/syncforge/actions/runs/28849313238), verify [run #1](https://github.com/Arsenoal/syncforge/actions/runs/28852404760))                                     | CI ✅      |
| 1.0-P0-06 | **Docs freeze** — CHANGELOG, MODULES, GETTING_STARTED match 1.0 APIs exactly                                                                                                                                                                                  | Docs ✅    |

### P1 (strongly recommended for 1.0.0; may slip to 1.0.1)

| ID        | Job                                                                   | Area      |
|-----------|-----------------------------------------------------------------------|-----------|
| 1.0-P1-01 | Upgrade guide: pre-0.6 Room → 0.6+ SQLDelight                         | Docs      |
| 1.0-P1-02 | Conflict recipes for 3+ entity types in RECIPES.md                    | Docs      |
| 1.0-P1-03 | Consumer ProGuard/R8 rules documented + tested (`consumer-rules.pro`) | Android   |
| 1.0-P1-04 | Integration tests: retry exhaustion, multi-page pull, offline queue   | QA ✅     |
| 1.0-P1-05 | Performance test: 1000+ outbox entries, batch push                    | QA        |
| 1.0-P1-06 | Security doc pass: TLS, token storage, no secrets in logs (expanded in 1.1-17/18) | Docs      |
| 1.0-P1-07 | At least one external dogfood or documented third-party integration   | Community |

### Explicitly post-1.0 (do not block 1.0)

- Desktop sample app (`:sample-desktop`)
- **`EntityStore` abstraction** — formal contract + KSP beyond Room DAOs (see 1.1.x)
- **`SyncHttpClient` abstraction** — injectable Ktor `HttpClient`; SyncForge maps push/pull routes (see 1.1.x)
- DataStore KMP cursor
- CRDT / DI integration artifacts
- GraphQL / Supabase / Spring transport adapters
- iOS SPM binary distribution
- Shake-to-open debug console
- **BOM simplification** — At 1.0 most apps declare only `studio.syncforge:syncforge` (KSP is pinned by `studio.syncforge.android`; runtime modules are transitive). Re-evaluate whether `syncforge-bom` stays published once optional store/transport artifacts land (1.1–1.4). If consumers rarely pull multiple versioned coordinates, deprecate the BOM in favor of the published version catalog (1.4-05) or a single explicit version on `syncforge` only. See [Distribution notes](#distribution-notes-10--20) below.

### Distribution notes (1.0 → 2.0)

**Maven Central (policy):** **1.x tags do not publish new artifacts to Maven Central.** Development continues on `main` with git tags, `publishAllToMavenLocal`, and optional manual **Publish Release** validation (macOS compile/test). **First Central upload for the 1.x line after 1.1.0 is `v2.0.0`.** Existing Central versions (1.0.0, 1.1.0) remain available. See [MAVEN_PUBLISH.md](MAVEN_PUBLISH.md).

**iOS SPM / XCFramework (policy):** Same **2.0.0+** gate as Maven Central. Until then, iOS consumers integrate via KMP frameworks (`linkIosFrameworksForXcode`, Xcode Run Script). `publishIosSpmArtifacts` is a gated placeholder (1.3-04). See [IOS_SETUP.md](IOS_SETUP.md) and [RELEASE.md](RELEASE.md).

**GitHub Releases:** Created **manually** in the repository UI — tag push does not auto-create releases or trigger publish CI.

**BOM at 1.0:** `syncforge-bom` constrains five library artifacts (`syncforge`, `annotations`, `ksp`, `persistence`, `android-deps`). It is **optional** for the common Android path — `implementation("studio.syncforge:syncforge")` plus the Gradle plugin is enough; GETTING_STARTED already marks the BOM as optional.

**When the BOM helps:** Apps that add several SyncForge modules explicitly (e.g. optional `:syncforge-store-*`, `:syncforge-transport-*` in 1.1+) without repeating a version on each line.

**When to drop it:** If post-1.0 optional artifacts stay few and the version catalog (1.4-05) covers consumer pins, remove or deprecate `syncforge-bom` before 2.0 to reduce boilerplate (`platform(...)` + version on plugin anyway). Decision gate: **1.4.0** acceptance — catalog published and docs show BOM-free minimal setup; only keep BOM if multi-artifact consumers are common.

### 1.0.0 API stability contract

**Stable at 1.0 (no opt-in required):**

- `SyncForge.android { }`, `SyncForgeAndroid.workManagerConfiguration`
- `SyncManager` — sync lifecycle, outbox enqueue, conflict resolution, scheduling hooks
- `ConflictPolicy`, `conflicts { }` — **per-entity** strategy (`entity("notes") { alwaysRemote() }`, `entity("tasks") { merge { } }`, …), `ConflictChoice`, `resolveConflict()`
- Compose production UI — `SyncStatusUiModel`, `collectSyncStatusUiModel()`, conflict chip/sheet
- `databaseName()`, KSP-generated handlers (Room DAO path — default DX)

**Store-agnostic at 1.0 (manual integration):**

- `EntitySyncHandler` / `TypedEntitySyncHandler` + `SyncedEntity` — any database can integrate by implementing CRUD + JSON; Room is not required at the engine layer (Realm and others work via hand-written handlers today)

**Experimental at 1.0 (may change in 1.x minors):**

- `SyncForge.ios { }`, `SyncForge.desktop { }`, `SyncForge.macos { }`
- `SyncForge.create()` / `SyncForgeBuilder` (low-level factory)
- Built-in `auth { }` DSL and `SyncManager.register`/`login`/`logout`
- `SyncManager.debug`, `conflictHistory`, `SyncDebug*`
- `SyncForgePersistence` custom wiring extensions
- Custom `SyncTransport` (e.g. GraphQL) — implement `push`/`pull`; wire via `transport { }` on platform DSLs

---

## 1.1.x — Integration & persistence polish

### Goal

Reduce boilerplate for real apps: **pluggable entity stores**, **injectable Ktor HTTP client** (interceptors, shared app `HttpClient`), dependency injection, smoother auth (**encrypted token storage**, safer credential APIs), unified cursor storage. Core sync semantics unchanged.

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
| 1.1-09 | **`EntityStore` contract** — formal app-side abstraction in `commonMain`                 | P0       | `findById`, `upsert`, `delete`, optional `transaction { }`; maps to `EntitySyncHandler` |
| 1.1-10 | **KSP `@SyncForgeStore`** — generate handlers from any `EntityStore` impl                | P0       | Keeps `@SyncForgeDao` (Room) as one adapter; not the only path                    |
| 1.1-11 | **Store adapter modules** — optional artifacts, not in core BOM                          | P1       | e.g. `:syncforge-store-room` (current DAO path), `:syncforge-store-realm`, in-memory for tests |
| 1.1-12 | **Docs + Gradle plugin** — Room optional; “sync-aware entity” not “Room entity”          | P1       | GETTING_STARTED branch for BYO store; `studio.syncforge.android` skips Room KSP when unused |
| 1.1-13 | **`SyncHttpClient` contract** — Ktor-backed REST executor in `commonMain`                | P0       | `postPush` / `getPull` with auth + status mapping; DTOs from `dev.syncforge.network.api` |
| 1.1-14 | **`RestSyncTransport`** — default REST transport using injected `SyncHttpClient`         | P0       | Refactor `KtorSyncTransport` to delegate here; paths `/sync/push`, `/sync/pull` configurable |
| 1.1-15 | **`:syncforge-network-ktor`** adapter (extract from core)                                | P1       | Default Ktor `HttpClient` path; share auth refresh with `RefreshingSyncAuthProvider` |
| 1.1-16 | **DSL `httpClient { }`** on platform DSLs                                                | P1       | `httpClient(appHttpClient)` or `httpClient(SyncHttpClient)`; falls back to bundled Ktor if omitted |
| 1.1-17 | **Encrypted `TokenStore`** — platform secure storage for access/refresh tokens           | P1       | Android: EncryptedSharedPreferences / Keystore; iOS: Keychain; JVM/desktop: documented secure option |
| 1.1-18 | **`CharArray` credential APIs** — `login`/`register` with wipe-after-use semantics       | P1       | Optional overloads alongside `Map<String, String>`; AUTH_API + BEST_PRACTICES security pass |

### Auth security architecture (1.1)

SyncForge does **not** persist passwords — only tokens. Security work targets **token at-rest** and **credential handling in memory**:

```
Login UI (SecureTextField)
        │
        ├── preferred: external IdP (Firebase Auth) → SyncAuthProvider only (no password in SyncForge)
        │
        └── built-in auth:
                login(email, password: CharArray)  ← 1.1-18; wipe in finally { }
                        │
                        ▼
                POST /auth/login (HTTPS) — password never stored
                        │
                        ▼
                TokenStore.save(access, refresh)  ← 1.1-17 encrypted at rest
                        │
                        ▼
                SyncAuthProvider → push/pull Bearer header
```

| Concern | 1.0 today | Target 1.1 |
|---------|-----------|------------|
| Password persistence | Not stored | Unchanged — never stored |
| Password in memory | `Map<String, String>` only | Optional `CharArray` overload + documented wipe |
| Token at rest | SharedPreferences (Android), UserDefaults (iOS), in-memory (JVM) | Encrypted platform stores |
| TLS | App/backend responsibility | Documented in security pass (`1.0-P1-06` / AUTH_API) |
| BYO IdP | `SyncAuthProvider.refreshing` | Unchanged — recommended for production |

**`CharArray` API (target 1.1-18)** — additive; existing `login(Map<String, String>)` remains:

```kotlin
suspend fun login(email: String, password: CharArray): AuthResult
suspend fun register(email: String, password: CharArray): AuthResult
// Implementation: convert to JSON body inside try/finally; password.fill('\u0000') in finally
// Note: JSON/HTTP may still allocate transient Strings — document limits in BEST_PRACTICES
```

### Entity store architecture (1.1)

SyncForge separates **your app database** from **SyncForge’s internal outbox/conflict DB** (SQLDelight, unchanged):

```
┌─────────────────────────────────────────────────────────────┐
│  Your app entities (any store)                              │
│  Room · Realm · SQLDelight · custom — via EntityStore       │
└───────────────────────────┬─────────────────────────────────┘
                            │ EntityStore / EntitySyncHandler
┌───────────────────────────▼─────────────────────────────────┐
│  :syncforge (commonMain)                                    │
│  SyncManager · outbox → push → pull · ConflictPolicy        │
└───────────────────────────┬─────────────────────────────────┘
                            │ OutboxRepository · ConflictStore
┌───────────────────────────▼─────────────────────────────────┐
│  SyncForge internal DB (SQLDelight syncforge.db)            │
│  user may inject SyncForgePersistence (experimental)        │
└─────────────────────────────────────────────────────────────┘
```

**Contract (target 1.1):**

| Type | Role |
|------|------|
| `SyncedEntity` | Metadata columns every synced row exposes (`id`, `localVersion`, `updatedAtMillis`, `syncState`) |
| `EntityStore<T>` | App-provided CRUD + optional transactions |
| `EntitySyncHandler` | Bridge from store to sync engine (generated or hand-written) |
| `@SyncForgeEntity` | Declares entity type + JSON shape for KSP |
| `@SyncForgeDao` | Room adapter (1.0 default) |
| `@SyncForgeStore` | Generic store adapter (1.1+) |

**Realm / other ORMs:** implement `EntityStore` (or `TypedEntitySyncHandler` manually at 1.0); KSP generates the handler in 1.1. No Realm dependency in `:syncforge` core.

### Entity JSON mapping (1.0 → 1.1)

SyncForge does **not** deserialize network responses straight into Room rows. The flow has two JSON layers:

```
Your @SyncForgeEntity + @Serializable data class
        │
        ▼  KSP generates *EntitySyncHandler
   toJson(entity) / fromJson(string)     ← kotlinx.serialization on your entity type
        │
        ▼  outbox entry or pull delta
   payloadJson: String                   ← entity JSON embedded in sync envelope
        │
        ▼  KtorSyncTransport
   PushRequest / PullResponse DTOs        ← dev.syncforge.network.api wire types
        │
        ▼  POST /sync/push · GET /sync/pull
   Server
```

| Layer | What is serialized | Who parses |
|-------|-------------------|------------|
| **Entity** | Your Room (or store) row as JSON | KSP-generated handler (`toJson` / `fromJson`) |
| **Transport** | `OutboxEntryDto`, `RemoteDeltaDto` with `payloadJson` string fields | `KtorSyncTransport` + kotlinx.serialization |

On **push**, the handler serializes the entity → `payloadJson` on the outbox entry → transport wraps entries in `PushRequest`. On **pull**, transport parses `PullResponse` → each delta’s `payloadJson` → handler `fromJson` → DAO upsert (via `ConflictPullApplier`). Ktor handles REST HTTP; handlers own entity JSON — no separate HTTP codegen layer.

### Network client architecture (1.1)

Three layers — sync semantics, REST routing, and Ktor HTTP:

```
┌─────────────────────────────────────────────────────────────────┐
│  SyncManager — outbox, push/pull orchestration (unchanged)       │
└────────────────────────────┬────────────────────────────────────┘
                             │ SyncTransport
              ┌──────────────┴──────────────┐
              │                             │
       RestSyncTransport            GraphQlSyncTransport (1.4)
       (REST push/pull routes)      (separate wire format)
              │
              │ uses SyncHttpClient (Ktor-backed)
    ┌─────────┴─────────┬─────────────┐
    │                   │             │
 bundled Ktor      user's HttpClient  custom SyncHttpClient
 (default)          (interceptors)     (advanced)
 (1.1-15)          (1.1-16 DSL)
```

**`SyncHttpClient` (target 1.1)** — Ktor REST executor; SyncForge maps sync DTOs to routes:

| Method | Route (default) | Body / params |
|--------|-----------------|---------------|
| `postPush(baseUrl, PushRequest, auth)` | `POST {baseUrl}/sync/push` | JSON batch from outbox |
| `getPull(baseUrl, since, types, limit, cursor, auth)` | `GET {baseUrl}/sync/pull` | Query params per REST_API.md |

User may supply an existing app **`HttpClient`** (logging, auth interceptors, shared engine). SyncForge does **not** add alternate HTTP stacks — Ktor is the sole REST client.

**Example (target DSL):**

```kotlin
SyncForge.android(this) {
    baseUrl("https://api.example.com")
    httpClient(appHttpClient)  // user's Ktor HttpClient
    registry(SyncForgeHandlers.registry(taskDao))
}
// → RestSyncTransport uses the injected client for /sync/push and /sync/pull only
```

**Today (1.0):** `KtorSyncTransport` bundles Ktor end-to-end, or implement full `SyncTransport` for non-REST backends. **1.1** splits transport from injectable `HttpClient` so apps reuse their Ktor setup without reimplementing push/pull mapping.

### DI architecture (1.1)

```
:syncforge                    ← no Koin/Dagger/Room/Realm dependency (unchanged)
:syncforge-integration-koin   ← optional, depends on koin-core
:syncforge-integration-hilt   ← optional, Android-only
:syncforge-store-room         ← optional, Room DAO → EntityStore adapter
:syncforge-store-realm        ← optional, Realm → EntityStore adapter
:syncforge-network-ktor       ← optional, default Ktor SyncHttpClient (extract from core)
```

App always supplies: `baseUrl`, `EntityRegistry` (handlers or stores), `conflicts { }`, and optionally `httpClient`. Library supplies factory helpers and optional adapters only.

### 1.1.0 GitHub issues breakdown

Use milestone **`1.1.0`** on GitHub. Labels: `epic:*`, `area:network|store|security|dx|docs`, `priority:p0|p1|p2`.

**Release goal:** Ship **1.1.0** with P0 network + entity-store tracks complete, security/cursor/docs P0 done, no breaking changes to 1.0 stable APIs. P1 integration artifacts (`syncforge-integration-*`, `syncforge-store-realm`) may land in **1.1.1** if needed.

#### Epic A — Injectable HTTP client (P0)

| Issue | Title | Job | Depends on | Done when |
|-------|-------|-----|------------|-----------|
| [#A1](.) | `feat(network): add SyncHttpClient contract in commonMain` | 1.1-13 | — | `postPush` / `getPull` API in `commonMain`; unit tests with fake client |
| [#A2](.) | `feat(network): RestSyncTransport delegates to SyncHttpClient` | 1.1-14 | A1 | `KtorSyncTransport` unchanged externally; delegates to `RestSyncTransport` internally |
| [#A3](.) | `refactor(network): extract :syncforge-network-ktor adapter` | 1.1-15 | A2 | Ktor `HttpClient` wiring moved to optional module; core compiles without Ktor on JVM-only consumers if applicable |
| [#A4](.) | `feat(dsl): httpClient { } on Android/iOS/desktop DSLs` | 1.1-16 | A2 | Sample or consumer-smoke compiles with injected app `HttpClient`; falls back to bundled client when omitted |
| [#A5](.) | `docs(network): injectable HttpClient guide + RECIPES` | 1.1-12 (partial) | A4 | GETTING_STARTED + RECIPES show interceptors / shared engine pattern |

#### Epic B — EntityStore abstraction (P0)

| Issue | Title | Job | Depends on | Done when |
|-------|-------|-----|------------|-----------|
| [#B1](.) | `feat(store): EntityStore contract in commonMain` | 1.1-09 | — | `findById`, `upsert`, `delete`, optional `transaction`; maps to existing `EntitySyncHandler` |
| [#B2](.) | `feat(ksp): @SyncForgeStore handler generation` | 1.1-10 | B1 | KSP emits handlers from `@SyncForgeStore`; `@SyncForgeDao` path unchanged |
| [#B3](.) | `feat(store): :syncforge-store-room adapter module` | 1.1-11 | B2 | Room DAO → `EntityStore` bridge published; not transitive in core BOM |
| [#B4](.) | `feat(store): in-memory EntityStore for commonTest` | 1.1-11 | B1 | Test module proves non-Room path without Realm dependency |
| [#B5](.) | `docs(store): BYO store path in GETTING_STARTED` | 1.1-12 | B2, B4 | Room optional narrative; Gradle plugin skips Room KSP when unused |

#### Epic C — Auth & token security (P1 → required for 1.1.0 GA)

| Issue | Title | Job | Depends on | Done when |
|-------|-------|-----|------------|-----------|
| [#C1](.) | `feat(auth): encrypted TokenStore on Android + iOS Keychain` | 1.1-17 | — | Migration from plain prefs documented; tokens never plain-text at rest |
| [#C2](.) | `feat(auth): login/register CharArray overloads` | 1.1-18 | — | Additive APIs; `finally { password.fill('\u0000') }`; AUTH_API + BEST_PRACTICES updated |
| [#C3](.) | `api(auth): graduate built-in auth DSL to stable` | 1.1-04 | C1, C2 | Remove `@ExperimentalSyncForgeApi` from `auth { }`, `authState`, register/login/logout after soak |

#### Epic D — DI & developer experience (P0 docs, P1 artifacts)

| Issue | Title | Job | Depends on | Done when |
|-------|-------|-----|------------|-----------|
| [#D1](.) | `docs(dx): Koin + Hilt recipes in RECIPES.md` | 1.1-01 | B1 | Copy-paste modules; no Koin/Dagger in `:syncforge` core |
| [#D2](.) | `feat(dx): publish syncforge-integration-koin` | 1.1-02 | D1 | Optional artifact; `syncForgeModule { }` + WorkManager helper |
| [#D3](.) | `feat(dx): publish syncforge-integration-hilt` | 1.1-03 | D1 | Optional artifact; `@Provides` templates |
| [#D4](.) | `feat(sample): optional :sample-di variant` | 1.1-06 | D2 or D3 | Documented fork or module showing DI wiring |

#### Epic E — Cursor persistence (P1)

| Issue | Title | Job | Depends on | Done when |
|-------|-------|-----|------------|-----------|
| [#E1](.) | `feat(persistence): DataStore Preferences pull cursor (Android)` | 1.1-05 | — | Replaces SharedPreferences cursor; iOS UserDefaults fallback documented |

#### Epic F — 1.1.0 release gate

| Issue | Title | Job | Depends on | Done when |
|-------|-------|-----|------------|-----------|
| [#F1](.) | `chore(dist): BOM lists optional 1.1 artifacts` | acceptance | A3, B3, D2 | BOM constraints for store/network/integration modules; not transitive |
| [#F2](.) | `test(qa): 1.1.0 acceptance matrix` | § below | A*, B*, C*, D1, E1 | All 1.1.0 acceptance checkboxes green in CI |
| [#F3](.) | `chore(release): tag v1.1.0 + Maven Central publish` | 1.1-08 lane | F2 | `CHANGELOG [1.1.0]`; verify workflow; semver minor bump |
| [#F4](.) | `docs: MODULES stability table + CHANGELOG 1.1.0` | — | F2 | Docs freeze for 1.1 APIs |

#### Suggested implementation order

```
Week 1–2   A1 → A2 (parallel) B1 → B2
Week 3–4   A4, A3 (optional extract) · B3, B4 · E1
Week 5     C1, C2 · D1 · B5, A5
Week 6     D2/D3 (if time) · F1 · acceptance + F2 → F3
```

**Parallel tracks:** Epic A and Epic B have no hard dependency on each other — split across two contributors if available.

**1.0.x patch lane (1.1-08):** Bugfixes only on `1.0.x` branch while `main` targets 1.1.0; no new APIs on patch line.

#### GitHub milestone checklist (copy into milestone description)

```markdown
## 1.1.0 — Wire-up
- [x] SyncHttpClient + RestSyncTransport (backward compatible KtorSyncTransport)
- [x] httpClient { } DSL + documented injectable HttpClient sample
- [x] EntityStore + @SyncForgeStore KSP (+ Room adapter module)
- [x] Non-Room path documented (in-memory store test or recipe)
- [x] Encrypted TokenStore + CharArray auth overloads
- [x] DataStore cursor (Android) + iOS fallback docs
- [x] RECIPES.md DI section (Koin + Hilt)
- [x] BOM optional artifacts listed; no 1.0 API breaks
```

---

### 1.1.0 acceptance criteria

- [x] `SyncHttpClient` + `RestSyncTransport` published; `KtorSyncTransport` delegates through them (backward compatible)
- [x] Injectable Ktor `HttpClient` documented with sample using app-owned client (interceptors, shared engine)
- [x] `EntityStore` published in `commonMain`; `EntitySyncHandler` delegates through it
- [x] KSP generates handlers from `@SyncForgeStore` and existing `@SyncForgeDao`
- [x] At least one non-Room path documented (Realm recipe or in-memory `EntityStore` test module)
- [x] RECIPES.md DI section with working Koin + Hilt examples matching `:sample`
- [x] DataStore cursor on Android; file/UserDefaults fallback documented for iOS until unified
- [x] Encrypted `TokenStore` default on Android; iOS Keychain; migration from plain SharedPreferences documented
- [x] `login`/`register` `CharArray` overloads published; AUTH_API documents wipe semantics and IdP preference
- [x] BOM lists optional integration + store artifacts (not transitive)
- [x] No breaking changes to 1.0 stable APIs (Room KSP path remains default)

---

## 1.2.x — Conflict evolution (per-entity strategies + git-like merge)

### Goal

Let apps pick a **conflict resolver per entity type** (notes → accept-remote, tasks → merge, settings → last-write-wins, …) from a **catalog of built-in strategies**, with optional **runtime overrides** from app preferences. Add **git-like three-way merge** (`gitLike { }`) that tries auto-merge then falls back to user choice (accept local / accept remote / custom merge). Reduce hand-written `merge { }` via CRDT helpers and KSP — **without replacing** outbox → push → pull.

### Per-entity conflict strategies

**Already in 1.0 (stable):** each synced entity type gets its own resolver in `conflicts { }`:

```kotlin
SyncForge.android(this) {
    conflicts {
        default(lastWriteWins())  // optional — this is the default

        entity("notes") { alwaysRemote() }   // accept-remote on pull conflict
        entity("tasks") {
            merge<TaskEntity> { local, remote -> /* field combine */ }
        }
        entity("settings") { alwaysRemote() }
        entity("drafts") { alwaysLocal() }
        entity("legal_records") { deferToUser() }  // user picks: local | remote | merge
    }
}
```

| Built-in strategy (1.0) | Git analogue | Resolution |
|-------------------------|--------------|------------|
| `lastWriteWins()` | Implicit timestamp merge | Auto — newer `updatedAtMillis` wins |
| `alwaysLocal()` | `--ours` | Auto — accept local |
| `alwaysRemote()` | `--theirs` | Auto — accept remote |
| `merge { }` | Custom merge driver | Auto — your field-level combine |
| `deferToUser()` | Conflict markers → user | Manual — `KeepLocal` / `AcceptRemote` / `Custom(merged)` |

**Target 1.2 — strategy catalog + app customization:**

```kotlin
// Pick from catalog — static or from app settings / DataStore
conflicts {
    entity("notes") { strategy(ConflictStrategies.acceptRemote) }
    entity("tasks") { strategy(userPrefs.tasksStrategy) }  // runtime-selected kind
    entity("collab_docs") {
        gitLike<DocEntity> {
            threeWayMerge { base, local, remote -> /* auto-merge non-overlapping fields */ }
            onUnmergeable { deferToUser() }  // fall back to accept local | remote | merge UI
        }
    }
}

// Optional: change policy at runtime (e.g. settings screen)
syncManager.updateConflictPolicy(conflictPolicy { /* … */ })
```

| `ConflictStrategyKind` (target 1.2) | Maps to |
|-------------------------------------|---------|
| `ACCEPT_LOCAL` | `alwaysLocal()` |
| `ACCEPT_REMOTE` | `alwaysRemote()` |
| `MERGE` | `merge { }` or KSP-generated merge |
| `GIT_LIKE` | `gitLike { }` — three-way auto + `deferToUser` fallback |
| `DEFER_TO_USER` | `deferToUser()` |
| `LAST_WRITE_WINS` | `lastWriteWins()` |
| `CRDT` | `crdt { }` (experimental) |

### Git-like merge architecture (target 1.2)

Two-way merge (1.0) compares **local + remote** only. Git-like merge adds a **merge base** (last synced snapshot):

```
Last successful sync          Local edit (PENDING)       Remote delta (pull)
        │                            │                          │
        ▼                            ▼                          ▼
   mergeBaseJson              local entity               remote entity
        │                            │                          │
        └──────────── threeWayMerge(base, local, remote) ──────┘
                              │
                    ┌─────────┴─────────┐
                    │                   │
              auto-merged            unmergeable fields
              → SYNCED + push          → deferToUser()
                                       → KeepLocal | AcceptRemote | Custom
```

Merge base storage: persist last-synced entity JSON per `(entityType, entityId)` on push ack or pull apply (see `1.2-07`).

### Features

| ID     | Job                                                                            | Priority | Notes                                          |
|--------|--------------------------------------------------------------------------------|----------|------------------------------------------------|
| 1.2-01 | **CRDT primitives** — `LwwRegister<T>`, `OrSet<T>`, `GCounter` in `commonMain` | P0       | Serializable; usable inside `merge { }` / `crdt { }` |
| 1.2-02 | **`crdt { }` conflict strategy** (experimental)                                | P0       | Per-field CRDT config in `conflicts { }`       |
| 1.2-03 | **KSP field-merge annotations** — `@Lww`, `@OrSet`, `@GCounter`                | P1       | Generates merge logic into handlers            |
| 1.2-04 | **Tombstone-aware merge recipes**                                              | P1       | Delete vs update; when to keep `deferToUser()` |
| 1.2-05 | **Multi-device E2E (single emulator concurrent edit)**                         | P1       | Validates LWW + merge + defer paths            |
| 1.2-06 | **CONFLICT_RESOLUTION.md v2**                                                  | P1       | Per-entity matrix; git-like flow; strategy catalog |
| 1.2-07 | **Merge-base snapshot store**                                                  | P0       | Last-synced JSON per `(entityType, entityId)`; feeds three-way merge |
| 1.2-08 | **`gitLike { }` strategy** — `threeWayMerge` + `onUnmergeable { deferToUser() }` | P0    | Accept local / accept remote / custom merge fallback |
| 1.2-09 | **`ConflictStrategyKind` catalog** — enum + `ConflictStrategies.fromKind()`    | P0       | App selects resolver per entity from all built-in types |
| 1.2-10 | **Runtime policy updates** — `updateConflictPolicy()` + preference-driven DSL  | P1       | `:sample` Policy tab + DataStore persistence ✅ |
| 1.2-11 | **Outbox reconcile on resolve** — clear stale outbox on `AcceptRemote`; enqueue hint on `Custom` | P1 | Keeps push state aligned with user/git-like resolution |

### Conflict strategy matrix (target 1.2)

| Entity (example)         | Recommended strategy                     | Why |
|--------------------------|------------------------------------------|-----|
| **Notes** (`:sample`)    | `alwaysRemote()` / `ACCEPT_REMOTE`       | Server-owned content; device accepts server copy |
| **Tasks** (`:sample`)    | `merge { }` or `gitLike { }`             | Independent fields (title vs completed); or three-way with user fallback |
| **Settings / config**    | `alwaysRemote()`                         | Server is source of truth |
| **Tags, collaborators**  | `crdt { field("tags") { orSet() } }`     | Additive set merges |
| **Counters**             | `crdt { field("views") { gCounter() } }` | Increment-only |
| **Legal / high-value**   | `deferToUser()` or `gitLike` fallback    | Human must confirm |
| **Low-stakes rows**      | `lastWriteWins()`                        | Simplest default |

`:sample` may adopt notes=`alwaysRemote`, tasks=`merge` or `gitLike` as reference wiring in 1.2.

### 1.2.0 acceptance criteria

- [x] `CrdtMergeStrategy` implements `ConflictStrategy`; wired through `ConflictPullApplier`
- [x] `gitLike { }` + merge-base store: three-way auto-merge unit tests; unmergeable fields fall back to `deferToUser` UI
- [x] `ConflictStrategyKind` catalog documented; app can assign a kind per `entityType` (static + runtime)
- [x] Unit tests per CRDT primitive + integration test for concurrent tag merge on pull
- [x] `crdt { }` and `gitLike { }` marked `@ExperimentalSyncForgeApi` until 2.0 graduation
- [x] Delete-conflict E2E still passes; notes accept-remote + tasks merge/gitLike covered in sample or RECIPES
- [x] Outbox reconciled after `AcceptRemote` / documented `enqueueChange` after `Custom` merge

**Audit (2026-07-08)** — evidence for sign-off:

| Criterion | Evidence |
|-----------|----------|
| CRDT + pull applier | [`CrdtMergeStrategy`](../../syncforge/src/commonMain/kotlin/dev/syncforge/conflict/CrdtMergeStrategy.kt); [`CrdtMergeStrategyTest.pullApplier_mergesConcurrentTagsOnConflict`](../../syncforge/src/commonTest/kotlin/dev/syncforge/conflict/CrdtMergeStrategyTest.kt) |
| gitLike + merge base + defer UI | [`GitLikeMergeStrategyTest`](../../syncforge/src/commonTest/kotlin/dev/syncforge/conflict/GitLikeMergeStrategyTest.kt), [`ConflictStrategyPullApplierE2ETest`](../../syncforge/src/commonTest/kotlin/dev/syncforge/conflict/ConflictStrategyPullApplierE2ETest.kt), [`MergeBasePullApplierTest`](../../syncforge/src/commonTest/kotlin/dev/syncforge/conflict/MergeBasePullApplierTest.kt); E2E defer/resolve in [`ConflictStrategyE2ETest`](../../sample/src/androidTest/kotlin/dev/syncforge/sample/ui/ConflictStrategyE2ETest.kt) |
| Strategy catalog static + runtime | [`ConflictStrategyKind`](../../syncforge/src/commonMain/kotlin/dev/syncforge/conflict/ConflictStrategyKind.kt), [`conflictPolicyFromKinds`](../../syncforge/src/commonMain/kotlin/dev/syncforge/conflict/ConflictPolicy.kt), [`UpdateConflictPolicyTest`](../../syncforge/src/commonTest/kotlin/dev/syncforge/conflict/UpdateConflictPolicyTest.kt); docs [CONFLICT_RESOLUTION.md v2](CONFLICT_RESOLUTION.md) |
| CRDT primitive + tag merge tests | [`LwwRegisterTest`](../../syncforge/src/commonTest/kotlin/dev/syncforge/conflict/crdt/LwwRegisterTest.kt), [`OrSetTest`](../../syncforge/src/commonTest/kotlin/dev/syncforge/conflict/crdt/OrSetTest.kt), [`GCounterTest`](../../syncforge/src/commonTest/kotlin/dev/syncforge/conflict/crdt/GCounterTest.kt); concurrent tag union via `pullApplier_mergesConcurrentTagsOnConflict` |
| Experimental API markers | `@ExperimentalSyncForgeApi` on [`gitLike`](../../syncforge/src/commonMain/kotlin/dev/syncforge/conflict/ConflictPolicy.kt) / [`crdt`](../../syncforge/src/commonMain/kotlin/dev/syncforge/conflict/ConflictPolicy.kt) DSL |
| E2E delete + notes + tasks | [`SampleScenariosE2ETest.tasks_deleteConflict_resolveAcceptRemote_removesTask`](../../sample/src/androidTest/kotlin/dev/syncforge/sample/ui/SampleScenariosE2ETest.kt); notes/tasks/tag matrix in [`ConflictStrategyE2ETest`](../../sample/src/androidTest/kotlin/dev/syncforge/sample/ui/ConflictStrategyE2ETest.kt); [`RECIPES.md` `:sample` conflict matrix](RECIPES.md#sample-conflict-matrix-12); CI `androidE2e` green (28 tests) |
| Outbox reconcile | [`OutboxReconcileTest`](../../syncforge/src/commonTest/kotlin/dev/syncforge/conflict/OutboxReconcileTest.kt) (`AcceptRemote`, `Custom` merged); trailing push in [`SyncEngine.runFullSync`](../../syncforge/src/commonMain/kotlin/dev/syncforge/sync/SyncEngine.kt); [CONFLICT_RESOLUTION.md → Outbox reconcile](CONFLICT_RESOLUTION.md#full-sync-cycle-localversion-and-outbox-reconcile) |

**1.2 feature jobs (same audit):** 1.2-01 … 1.2-09, 1.2-10, and 1.2-11 ✅ shipped; 1.2-05 ✅ (`ConflictStrategyE2ETest` + CI); 1.2-06 ✅ (CONFLICT_RESOLUTION v2). **1.2-10** — `ConflictSettingsScreen` + `SampleConflictPolicyStore` (DataStore) + `ConflictSettingsE2ETest`.

---

## 1.3.x — Platform parity & distribution

### Goal

iOS and desktop are **first-class documented paths**, not compile-only targets. Improve distribution for non-Gradle consumers.

### Features

| ID     | Job                                                                           | Priority | Notes                                       |
|--------|-------------------------------------------------------------------------------|----------|---------------------------------------------|
| 1.3-01 | **`:sample-desktop`** — minimal CLI or Compose Multiplatform                  | P0       | Proves `SyncForge.desktop { }` ✅           |
| 1.3-02 | **Graduate iOS DSL** — `SyncForge.ios { }` stable after desktop + device soak | P1       | Requires 1.1 cursor + auth hardening ✅     |
| 1.3-03 | **Graduate desktop/macos DSLs**                                               | P1       | Pair with desktop sample ✅                 |
| 1.3-04 | **Swift Package Manager / XCFramework publish**                               | P1       | Gated to **2.0.0+** (like Maven); manual Publish Release workflow; IOS_SETUP.md update |
| 1.3-05 | **Compose Multiplatform conflict/debug UI**                                   | P2       | `composeMain` conflict dialog + desktop CMP demo ✅ |
| 1.3-06 | **Shake-to-open `SyncDebugLauncher`**                                         | P2       | Debug builds only                           |
| 1.3-07 | **SKIE Swift API review** — document recommended Swift patterns               | P1       | [SWIFT_INTEROP.md](SWIFT_INTEROP.md) + `ios-sample` Flow demo ✅ |

### 1.3.0 acceptance criteria

- [x] Desktop sample runs against `:mock-server` with push + pull
- [x] iOS consumer can integrate via documented KMP framework path ([IOS_SETUP.md](IOS_SETUP.md), [SWIFT_INTEROP.md](SWIFT_INTEROP.md)); SPM deferred to 2.0
- [x] Platform stability table in MODULES.md updated (iOS, desktop, macOS → Stable)
- [x] `iosE2e` + desktop smoke in CI (desktop may be JVM-only nightly)

---

## 1.4.x — Ecosystem & advanced QA

### Goal

Meet teams where their backend already lives — REST, GraphQL, or hosted BaaS — without changing the sync engine. Prove concurrent multi-device behavior.

### Features

| ID     | Job                                                   | Priority | Notes                                              |
|--------|-------------------------------------------------------|----------|----------------------------------------------------|
| 1.4-01 | **Spring Boot backend starter**                       | P0       | Uses `:syncforge-server` routes; JDBC store option ✅ |
| 1.4-02 | **`SyncDeltaStore` port + `DeltaStoreSyncTransport`** | P0       | General BaaS adapter in `:syncforge-transport-core`; one push/pull mapping for all backends ✅ |
| 1.4-03 | **`:syncforge-transport-supabase`** — `SyncDeltaStore` impl | P1  | Supabase Postgres / Realtime patterns ✅           |
| 1.4-04 | **`:syncforge-transport-firebase`** — `SyncDeltaStore` impl | P1  | Firestore (or Functions-backed store) ✅         |
| 1.4-05 | **Gradle version catalog for consumers**              | P1       | Published alongside BOM; gate for BOM deprecation (see [Distribution notes](#distribution-notes-10--20)) ✅ |
| 1.4-06 | **Multi-device E2E** — two emulators, concurrent edit | P1       | Conflict + CRDT validation ✅                      |
| 1.4-07 | **Backend contract test kit**                         | P2       | Shared harness for REST + `SyncDeltaStore` impls ✅ |
| 1.4-08 | **`syncforge-transport-graphql`** client adapter      | P1       | `SyncTransport` over Apollo/Ktor GraphQL; maps push/pull mutations + cursor query ✅ |
| 1.4-09 | **GraphQL schema + resolver recipes**                 | P1       | Sample `syncPush` / `syncPull` operations; RECIPES.md + optional `:backend-starter-graphql` ✅ |
| 1.4-10 | **Custom transport guide** — BYO `SyncTransport` or `SyncDeltaStore` | P2 | Works at 1.0 via `transport { }`; 1.4 adds store port ✅ |

### Transport architecture (1.4)

SyncForge separates **sync control** (what the engine needs) from **wire/storage** (how you reach the backend):

```
┌──────────────────────────────────────────────────────────────┐
│  :syncforge — SyncManager, outbox, conflicts (unchanged)     │
└────────────────────────────┬─────────────────────────────────┘
                             │ SyncTransport  ← general transport control (1.0)
        ┌────────────────────┼────────────────────┬──────────────────┐
        │                    │                    │                  │
 RestSyncTransport    DeltaStoreSyncTransport   GraphQlSyncTransport  …
 (1.1, REST/Ktor)      (1.4, general BaaS)      (1.4, GraphQL wire)
        │                    │
 SyncHttpClient         SyncDeltaStore  ← storage/query port (1.4-02)
        │                    │
   POST /sync/push     ┌──────┼──────┬──────────┐
   GET  /sync/pull     │      │      │          │
                  Firebase Supabase  Custom   JDBC
                  impl     impl     impl     (server SyncStore)
```

| Layer | Interface | Role |
|-------|-----------|------|
| **Transport control** | `SyncTransport` | What `SyncManager` calls — `push()` / `pull()` (stable 1.0) |
| **REST wire** | `SyncHttpClient` | Ktor HTTP for `/sync/push` + `/sync/pull` (1.1) |
| **BaaS / hosted store** | `SyncDeltaStore` | Append outbox batches + query deltas since cursor — **one contract, many backends** (1.4) |
| **General adapter** | `DeltaStoreSyncTransport` | Implements `SyncTransport` by delegating to any `SyncDeltaStore` — no per-vendor push/pull duplication |
| **Server-side mirror** | `SyncStore` (`:syncforge-server`) | Same semantics for self-hosted Ktor/Spring backends |

**`SyncDeltaStore` (target 1.4)** — client-side port; mirrors server `SyncStore`:

```kotlin
interface SyncDeltaStore {
    suspend fun appendEntries(entries: List<OutboxEntry>): PushResult
    suspend fun queryDeltas(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        pageSize: Int,
        pageCursor: String?,
    ): PullResult
}

// General adapter — works with any store implementation
class DeltaStoreSyncTransport(
    private val store: SyncDeltaStore,
    private val auth: SyncAuthProvider? = null,
) : SyncTransport { /* maps to push/pull once */ }
```

**Firebase / Supabase / custom:** implement `SyncDeltaStore` only (Firestore queries, Supabase RPC, etc.). Wire once:

```kotlin
SyncForge.android(this) {
    transport(DeltaStoreSyncTransport(FirestoreSyncDeltaStore(firestore)))
    // or: DeltaStoreSyncTransport(SupabaseSyncDeltaStore(client))
    registry(SyncForgeHandlers.registry(taskDao))
    conflicts { entity("notes") { alwaysRemote() } }
}
```

Optional modules ship ready-made `SyncDeltaStore` impls; apps with unusual schemas implement the port themselves (~100–200 lines) without touching `SyncManager`.

### How users adapt GraphQL

GraphQL is a **wire-format swap** — the sync engine, outbox, handlers, and entity JSON stay the same. Only `SyncTransport` changes.

**Contract (server):** expose two operations with the same semantics as [REST_API.md](REST_API.md) — not per-entity CRUD:

| REST (1.0 default) | GraphQL equivalent (your schema) |
|--------------------|----------------------------------|
| `POST /sync/push` body: `PushRequest` | `mutation syncPush(entries: [OutboxEntryInput!]!): PushPayload!` |
| `GET /sync/pull?since=…&types=…` | `query syncPull(since: Long!, types: [String!]!, limit: Int, cursor: String): PullPayload!` |

`payloadJson` on each entry/delta remains an opaque **string** — your entity handlers still parse it; GraphQL does not need to know your Room schema.

**Today (1.0) — bring your own transport:**

```kotlin
class GraphQlSyncTransport(
    private val client: ApolloClient, // or Ktor GraphQL, etc.
) : SyncTransport {

    override suspend fun push(entries: List<OutboxEntry>): PushResult {
        val response = client.mutation(SyncPushMutation(entries.map { it.toGraphQlInput() }))
        return response.data!!.syncPush.toPushResult()  // map to PushResult
    }

    override suspend fun pull(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        pageSize: Int,
        pageCursor: String?,
    ): PullResult {
        val response = client.query(
            SyncPullQuery(sinceTimestampMillis, entityTypes.toList(), pageSize, pageCursor),
        )
        return response.data!!.syncPull.toPullResult()  // map to PullResult
    }
}

SyncForge.android(this) {
    transport(GraphQlSyncTransport(apolloClient))
    registry(SyncForgeHandlers.registry(taskDao))
    // baseUrl() omitted — your transport owns the endpoint
}
```

You implement the mapping (`OutboxEntry` → mutation input, GraphQL response → `PushResult` / `PullResult`). Entity handlers and KSP codegen are unchanged.

**1.4 (optional convenience):** `:syncforge-transport-graphql` ships a ready-made `SyncTransport` (Apollo or Ktor GraphQL) plus schema/resolver recipes (`1.4-08`–`1.4-09`). BaaS backends use `DeltaStoreSyncTransport` + vendor `SyncDeltaStore` (`1.4-02`–`1.4-04`). Same contract; less boilerplate.

**Non-goals:** SyncForge does not generate GraphQL types from `@SyncForgeEntity`, and does not replace your app's domain GraphQL API. Sync operations are a dedicated push/pull channel alongside your existing queries/mutations.

### 1.4.0 acceptance criteria

- [x] `SyncDeltaStore` + `DeltaStoreSyncTransport` published in `:syncforge-transport-core` (optional BOM entry)
- [x] At least two `SyncDeltaStore` implementations (Supabase + Firebase) pass shared contract test kit
- [x] Spring starter documented with docker-compose quickstart (`:backend-starter-spring`)
- [x] `syncforge-transport-graphql` published; sample push/pull against `:mock-server` GraphQL facade or standalone schema
- [x] RECIPES.md: BYO `SyncDeltaStore`, custom `SyncTransport`, GraphQL schema snippet
- [x] Version catalog published (`:syncforge-catalog`); GETTING_STARTED shows BOM-free catalog setup
- [x] Multi-device E2E green in nightly CI (`androidMultiDeviceE2e`, workflow `android-multi-device-e2e.yml`)
- [x] REST_API.md documents transport adapter expectations (same push/pull semantics; wire format may differ)

---

## 1.5.x — Observability & production hardening

### Goal

Operators and senior developers can **see** sync health in production-like environments.

### Features

| ID     | Job                                  | Priority | Notes                                                       |
|--------|--------------------------------------|----------|-------------------------------------------------------------|
| 1.5-01 | **Structured tracing hooks**         | P0       | OpenTelemetry-compatible spans: push, pull, conflict, retry ✅ |
| 1.5-02 | **SyncHealth metrics expansion**     | P1       | Latency percentiles, conflict rate, outbox depth ✅          |
| 1.5-03 | **Full SyncHealth dashboard UI**     | P1       | Debug + optional release diagnostic screen ✅                |
| 1.5-04 | **Hierarchical sync recipes**        | P1       | Parent/child entities, orphan FK guidance ✅                 |
| 1.5-05 | **Rate limiting + backoff policies** | P2       | Server-friendly client behavior ✅                          |
| 1.5-06 | **Audit log export**                 | P2       | Conflict history CSV/JSON for support ✅                    |

### 1.5.0 acceptance criteria

- [x] Tracing opt-in does not allocate when disabled
- [x] Dashboard shows outbox, last sync, conflict count, error breakdown
- [x] BEST_PRACTICES.md hierarchical section with explicit limitations

---

## 1.6.x — Web add-on (optional)

### Goal

Bring SyncForge to **browser clients** as an **optional add-on** after native platform parity (1.3) and ecosystem transports (1.4). Does **not** block **2.0** — teams can ship 2.0 on Android/iOS/desktop without waiting for web.

Web reuses the same sync loop (outbox → push → pull → conflicts) and [REST_API.md](REST_API.md) contract. Entity storage and background sync follow browser constraints (no WorkManager; visibility/online-event driven sync).

### Scope choice (decide in 1.6-00 spike)

| Target | Pros | Cons |
|--------|------|------|
| **Kotlin/Wasm (`wasmJs`)** | Shares more KMP code with mobile/desktop; Compose Multiplatform Web path | Tooling + SQLDelight driver maturity; larger wasm bundle |
| **Kotlin/JS (`js`)** | Mature Ktor JS engine; smaller iteration cost | Fewer shared native APIs; persistence story is weaker |

Default recommendation: spike **Wasm** first if SQLDelight + Compose Web drivers are viable; fall back to **JS** for transport-only MVP.

### Features

| ID     | Job                                                                 | Priority | Notes |
|--------|---------------------------------------------------------------------|----------|-------|
| 1.6-00 | **Web platform spike** — Wasm vs JS, persistence + Ktor engine PoC  | P0       | Go/no-go doc; does not ship in BOM until 1.6-01+ green |
| 1.6-01 | **KMP `js` and/or `wasmJs` targets** on `:syncforge` + persistence | P0       | `webMain` source set; experimental `@ExperimentalSyncForgeApi` |
| 1.6-02 | **`SyncForge.web { }` DSL** — browser persistence + cursor + transport | P0    | IndexedDB/SQLDelight-web or documented in-memory + localStorage cursor fallback |
| 1.6-03 | **Ktor browser HTTP client** — `createKtorSyncTransport` for web      | P0       | `ktor-client-js` or wasm fetch engine |
| 1.6-04 | **`:sample-web`** — minimal Compose/Web or Kotlin/JS page           | P1       | Push + pull against `:mock-server` (same acceptance as `:sample-desktop`) |
| 1.6-05 | **`WEB_SETUP.md`** + MODULES.md stability row                       | P1       | Gradle consumer snippet; CORS notes for dev mock-server |
| 1.6-06 | **`webE2e` CI** — headless browser smoke (Playwright or Karma)      | P2       | Nightly; optional gate for 1.6.0 tag |
| 1.6-07 | **Conflict/debug UI on web** — share 1.3-05 CMP components          | P2       | Defer if 1.3-05 not yet on Wasm |

### 1.6.0 acceptance criteria (add-on release)

- [ ] `SyncForge.web { }` documented and compiles on at least one browser target (Wasm or JS)
- [ ] `:sample-web` runs push + pull against `:mock-server` locally
- [ ] Published as **optional** BOM artifacts (e.g. `syncforge-web` or platform-specific variants) — not required for Android-primary consumers
- [ ] BEST_PRACTICES.md FAQ row updated from “not in scope” to “1.6 add-on”
- [ ] Explicit limitations documented: no background sync guarantee, storage quotas, CORS/dev-server setup

### 1.6 explicit non-goals

- Replacing native mobile/desktop samples — web is additive
- Service Worker–only offline sync without the shared outbox engine
- Publishing a separate TypeScript/npm SDK (Kotlin/JS/Wasm interop is the path; TS BYO REST remains valid at 1.0)
- Blocking **2.0.0** on web stability

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

- Replacing the app’s chosen entity store — SyncForge integrates via `EntityStore` / handlers; it does not own app schema
- Full real-time WebSocket sync as the only mode
- Automatic CRDT for whole JSON blobs without schema
- Bundling Koin or Dagger into core `:syncforge`

### 2.0 architecture sketch

```
                    ┌─────────────────────────────────────┐
                    │           App entities              │
                    │  (Room / Realm / any EntityStore)   │
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
- [ ] Full CI matrix: Android E2E, iOS E2E, desktop smoke, consumer-smoke, multi-device (+ optional `webE2e` from 1.6 add-on)

---

## REST API evolution

| Library version | REST contract             | Notes                                                            |
|-----------------|---------------------------|------------------------------------------------------------------|
| 1.0.x           | **v1 frozen**             | `POST /sync/push`, `GET /sync/pull` per REST_API.md              |
| 1.x             | v1 + optional auth routes | `/auth/*` stable with built-in auth; **GraphQL** via `SyncTransport` adapter (1.4) — same semantics, different wire format |
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
| Per-entity strategy misconfiguration  | Medium   | `ConflictStrategyKind` catalog + CONFLICT_RESOLUTION v2 matrix; sample shows notes vs tasks |
| gitLike three-way merge complexity    | Medium   | Merge-base store; fall back to `deferToUser`; experimental until 2.0 |
| Token theft on rooted/jailbroken device | Medium | Encrypted `TokenStore` (1.1-17); recommend external IdP; no password storage |
| DI integration artifact fragmentation | Low      | Max two optional DI modules; recipes first in 1.1.0                              |
| Store adapter fragmentation (Realm…)  | Medium   | `EntityStore` in core; adapters optional; hand-written handler always works      |
| iOS SPM publish complexity            | Medium   | Ship XCFramework from existing macOS CI job; fallback to KMP framework         |
| REST v2 splits ecosystem              | High     | Defer to 2.0; v1 long support window; adapters implement v1 only in 1.x        |
| Multi-device E2E flakiness            | Medium   | Nightly only initially; mock-server health gate                                |
| Scope creep into “full backend”       | Medium   | Starters are reference kits; `:syncforge-server` stays minimal                 |
| GraphQL schema drift vs REST contract | Medium   | Document canonical push/pull semantics; adapter tests share contract test kit  |
| HTTP client adapter proliferation     | Low      | Ktor-only; `SyncHttpClient` in core; optional `:syncforge-network-ktor` extract |

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

Run automated checks locally:

```bash
./gradlew verifySignOffMatrix          # verifyReleaseSignOff (pre-tag)
# After portal Publish + Central sync:
#   Actions → Verify Maven Central Release (manual)
# or locally: verifyMavenCentralArtifacts + verifyConsumerSmokeMavenCentral
```

E2E runs in CI only (`androidE2e` on Linux emulator, `iosE2e` on `macos-14`).

| # | Criterion | Verification | Status (1.0.0 GA, July 2026) |
|---|-----------|--------------|------------------------------|
| 1 | All 1.0-P0 jobs complete | P0 table above | ✅ |
| 2 | Sample App Proof — 2+ entities/DAOs/screens, shared `SyncManager` | `androidE2e`, `iosE2e` | ✅ CI ([run #102](https://github.com/Arsenoal/syncforge/actions/runs/28838303714)) |
| 3 | No `@ExperimentalSyncForgeApi` on stable surfaces | `StableApiSurfaceTest`, `StableAndroidApiSurfaceTest` | ✅ in `verifyReleaseSignOff` |
| 4 | Deprecated APIs removed | grep + compile | ✅ `ConflictResolver`, `useSqlDelightPersistence`, `useRoomPersistence` gone |
| 5 | Maven Central all artifacts at release version | `verifyConsumerSmokeMavenCentralArtifacts` | ✅ [verify run #1](https://github.com/Arsenoal/syncforge/actions/runs/28852404760) |
| 6 | CI green | `verifyReleaseSignOff`, `androidE2e`, `iosE2e`, `verifyConsumerSmokeMavenCentral` | ✅ |
| 7 | macOS publish (iOS/macOS KMP targets) | `publish-release.yml` + Central sync | ✅ [publish run #29](https://github.com/Arsenoal/syncforge/actions/runs/28849313238) |
| 8 | Docs freeze | `CHANGELOG`, `MODULES`, `GETTING_STARTED` | ✅ P0-06 + `1.0.0` pins |
| 9 | Room → SQLDelight migration | `SyncForgeAndroidMigrationTest`, `RoomMigrationInstrumentedTest` | ✅ in `androidE2e` |
| 10 | External dogfood / third-party integration | Community | ⬜ deferred (P1-07) |

**1.0.0 verdict:** GA. Tag `v1.0.0` marks the stable baseline; row 10 remains optional community soak (P1-07).

### 1.1.0 sign-off checklist

Run automated checks locally:

```bash
./gradlew verifySignOffMatrix          # verifyReleaseSignOff (pre-tag)
# After portal Publish + Central sync:
#   Actions → Verify Maven Central Release (manual, version 1.1.0)
# or locally: verifyMavenCentralArtifacts -PverifyMavenCentralVersion=1.1.0 + verifyConsumerSmokeMavenCentral
```

| # | Criterion | Verification | Status (1.1.0 GA, July 2026) |
|---|-----------|--------------|------------------------------|
| 1 | All 1.1 epics complete (A–F) | Roadmap § 1.1.0 acceptance criteria | ✅ |
| 2 | No breaking changes to 1.0 stable APIs | `StableApiSurfaceTest`, `StableAndroidApiSurfaceTest` | ✅ in `verifyReleaseSignOff` |
| 3 | Built-in auth stable | Auth API guards + `AUTH_API.md` | ✅ |
| 4 | BOM lists optional 1.1 artifacts | `:syncforge-bom:verifyBomConstraints` | ✅ in `verifyReleaseSignOff` |
| 5 | Version catalog lists all library aliases + plugin | `:syncforge-catalog:verifyCatalogArtifacts` | ✅ in `verifyReleaseSignOff` |
| 5 | Optional modules publish to Maven Central | `publishAllToMavenCentral` + portal Publish (both deployments) | ✅ |
| 6 | CI green | Publish Release workflow + `verifyReleaseSignOff` | ✅ |
| 7 | Maven Central all artifacts at `1.1.0` | `verifyMavenCentralArtifacts` + `verifyConsumerSmokeMavenCentral` | ✅ |
| 8 | Docs freeze | `CHANGELOG [1.1.0]`, `MODULES`, `GETTING_STARTED` | ✅ |
| 9 | Encrypted tokens + cursor migration | `TokenStoreTest`, `SyncCursorStoreTest` | ✅ in `verifyReleaseSignOff` |

**1.1.0 verdict:** GA. Tag `v1.1.0` on Maven Central; 14 required POMs + consumer smoke verified locally.

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
| [CONFLICT_RESOLUTION.md](CONFLICT_RESOLUTION.md)                              | Strategy guide v2 (1.2 catalog + `:sample` matrix) |
| [MAVEN_PUBLISH.md](MAVEN_PUBLISH.md)                                          | Maven Central publish + verify workflow   |