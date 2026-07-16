# SyncForge

**Offline-first sync engine for Kotlin Multiplatform** — outbox, push/pull, and conflict policy on top of **your** entity store.

![Kotlin](https://img.shields.io/badge/kotlin-2.1+-7F52FF?logo=kotlin&logoColor=white)
![Maven Central](https://img.shields.io/maven-central/v/studio.syncforge/syncforge?color=0D7377)
![License](https://img.shields.io/github/license/Arsenoal/syncforge)
[![CI](https://github.com/Arsenoal/syncforge/actions/workflows/ci.yml/badge.svg)](https://github.com/Arsenoal/syncforge/actions/workflows/ci.yml)

![Android](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![iOS](https://img.shields.io/badge/platform-iOS-000000?logo=apple&logoColor=white)
![Desktop](https://img.shields.io/badge/platform-JVM%20desktop-007396?logo=openjdk&logoColor=white)
![macOS](https://img.shields.io/badge/platform-macOS-000000?logo=apple&logoColor=white)

**Latest on Maven Central:** [`2.0.0`](https://central.sonatype.com/namespace/studio.syncforge) · [Changelog](CHANGELOG.md#200---2026-07-09) · [Upgrade from 1.1.0](docs/UPGRADE_1_1_TO_2_0.md)

---

## What SyncForge is

SyncForge is a **client-side sync engine**, not a hosted backend or a replacement for Room/SQLDelight/Realm.

| You own | SyncForge owns |
|---------|----------------|
| Entity tables and schema | Durable **outbox** + **conflict** store (SQLDelight) |
| Domain JSON (`payloadJson` shape) | Push/pull loop, retries, pull cursor |
| UI and product rules | Per-entity **conflict policy** (LWW, merge, git-like, CRDT fields, defer-to-user) |
| Server that speaks the [REST push/pull contract](docs/REST_API.md) (or an adapter) | Optional transports (REST/Ktor, GraphQL, Supabase, Firebase, custom) |

Local writes go to **your** store first (optimistic). Mutations enqueue; sync drains the outbox when the network is available. Offline edits and app restarts are first-class.

> **UI / navigation:** SyncForge is not a navigation or MVI framework. For Compose multiplatform navigation and sync-aware chrome, see **[ForgeNav](https://github.com/Arsenoal/forgenav)** (`studio.forgenav`).

---

## Who it’s for

**Good fit when you need:**

- Offline-first mobile (or multiplatform) apps with **your own API**
- An outbox that survives process death, with explicit `sync()` / scheduled sync
- Per-entity conflict strategies without rewriting a sync stack
- Android-first delivery, with iOS / JVM desktop / macOS on the same model
- Self-hosted or BYO backend (Ktor/Spring starters and a mock server are in-repo)

**Not a fit when you want:**

- A managed BaaS that owns your schema and auth end-to-end (use Supabase/Firebase *as a transport*, or pick a full BaaS product)
- Automatic domain **schema evolution** for breaking JSON changes (you migrate Room + payloads; SyncForge does not version your entity model)
- Real-time collaborative editing as the primary mode (push/pull + optional realtime adapters; not a full CRDT document product)
- Drop-in Swift Package Manager / XCFramework install today (use **KMP frameworks** via Gradle — [iOS setup](docs/IOS_SETUP.md))
- Production browser install from Maven Central (web is **experimental** and **monorepo-only** — [Web setup](docs/WEB_SETUP.md))

---

## Current state (`2.0.0`)

| Area | Status |
|------|--------|
| **Maven Central** | Published under `studio.syncforge` — core, optional modules, version catalog, Android Gradle plugin |
| **Android** | **Stable** primary path — Room (or BYO store), WorkManager, Compose helpers, KSP handlers |
| **iOS / JVM desktop / macOS** | **Stable** platform DSLs — integrate as KMP framework (iOS); SPM/XCFramework still planned |
| **Conflicts** | **Stable** — LWW, always local/remote, merge, `gitLike`, `crdt`, `deferToUser` |
| **Transports** | **Stable** optional modules — REST default; GraphQL / Supabase / Firebase / `SyncDeltaStore` |
| **Auth DSL** | Built-in register/login/refresh helpers when your API matches the expected shape |
| **Debug / tracing / web** | **Experimental** — `@OptIn(ExperimentalSyncForgeApi::class)`; shapes may change |
| **Browser `js`** | Sources + `:sample-web` in this monorepo only — **not** on Maven Central |
| **Backend** | Contract + starters (`:mock-server`, `:backend-starter*`) — you run and operate the server |

Wire format: REST **v1** (`POST /sync/push`, `GET /sync/pull`) is frozen for the 2.x line. Entity payload schemas are **your** domain, not SyncForge semver. Details: [REST_API.md](docs/REST_API.md), [MODULES.md](docs/MODULES.md) (stability table).

---

## See it in action

<p align="center">
  <img src="docs/images/syncforge-demo.gif" alt="SyncForge demo: add task, sync, conflict resolution, clear local DB, pull from server" width="360" style="max-width: 360px; border-radius: 16px; border: 1px solid #e0e0e0;" />
</p>

```bash
./gradlew :mock-server:run          # Terminal 1 — reference API on :8080
./gradlew :sample:installDebug      # Terminal 2 — emulator uses http://10.0.2.2:8080
```

In the sample: **Sync**, force a **conflict**, or clear local data and pull. Debug builds expose an **SF** overlay for outbox depth and sync health.

---

## Quick start (Android)

Import the version catalog once (`2.0.0` pins every artifact and the Gradle plugin):

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    versionCatalogs {
        create("syncforge") {
            from("studio.syncforge:syncforge-catalog:2.0.0")
        }
    }
}
```

```kotlin
// app/build.gradle.kts
plugins {
    alias(syncforge.plugins.syncforge.android)
}
dependencies {
    implementation(syncforge.core)
}
```

```kotlin
// Application
syncManager = SyncForge.android(this) {
    baseUrl("https://api.example.com")
    registry(SyncForgeHandlers.registry(taskDao))
    schedulePeriodicSyncOnStart()
}

// Repository — write your DB, then enqueue
syncManager.enqueueChange(Change.create("tasks", task))
syncManager.sync()
```

**Full walkthrough (~10 min):** [Getting Started](docs/GETTING_STARTED.md)  
**iOS / desktop:** [iOS setup](docs/IOS_SETUP.md) · [Desktop setup](docs/DESKTOP_SETUP.md)

---

## Platforms

| Platform | Entry point | Distribution | Sample |
|----------|-------------|--------------|--------|
| **Android** | `SyncForge.android { }` | Maven Central | [`:sample`](sample/) |
| **iOS** | `SyncForge.ios { }` | Maven Central (KMP framework; not SPM yet) | [`:ios-sample`](ios-sample/) |
| **JVM desktop** | `SyncForge.desktop { }` | Maven Central | [`:sample-desktop`](sample-desktop/) |
| **macOS native** | `SyncForge.macos { }` | Maven Central | — |
| **Browser** | `SyncForge.web { }` *(experimental)* | Monorepo / composite / `publishToMavenLocal` only | [`:sample-web`](sample-web/) |

Gradle snippets and DSL options live in the setup guides — not duplicated here.

---

## Limitations (read this)

These are intentional product boundaries, not temporary bugs:

1. **Entity schema is yours** — SyncForge stores opaque `payloadJson`. Additive JSON changes are straightforward; **breaking** renames/refactors need your Room migration **and** a plan for pending outbox / server history. There is no built-in `schemaVersion` migrate API yet.
2. **You need a backend (or adapter)** — SyncForge does not host data. Implement the [push/pull contract](docs/REST_API.md) or use a provided transport against Supabase/Firebase/GraphQL.
3. **Not whole-document CRDT collab** — Field-level `crdt { }` / `gitLike { }` help merge concurrent edits; they are not a Notion-style collaborative document layer.
4. **iOS install path** — KMP framework from Gradle today; SwiftPM / XCFramework packaging is still on the roadmap.
5. **Web is experimental** — not published to Central; SQL.js worker stack; opt-in required.
6. **Optional modules are non-transitive** — store, transport, and integration artifacts are listed in the catalog; add them explicitly when needed.
7. **Multi-device E2E and samples prove the engine** — production hardening (auth, rate limits, observability) is documented; your ops and threat model remain yours.

Deeper guidance: [Best practices](docs/BEST_PRACTICES.md) · [Conflict resolution](docs/CONFLICT_RESOLUTION.md) · [Roadmap](docs/ROADMAP.md).

---

## Documentation

| | |
|---|---|
| **All features + samples** | [Feature catalog](docs/FEATURES.md) |
| **Copy-paste recipes** | [Recipes](docs/RECIPES.md) |
| **API / stability** | [Modules](docs/MODULES.md) |
| **Backend contract** | [REST API](docs/REST_API.md) |
| **Conflicts** | [Conflict resolution](docs/CONFLICT_RESOLUTION.md) |
| **Navigation + MVI (Compose)** | [ForgeNav](https://github.com/Arsenoal/forgenav) |
| **Doc index** | [docs/README.md](docs/README.md) |

### In-repo samples & backends

| Module | Role |
|--------|------|
| [`:sample`](sample/) | Android multi-entity sync, `gitLike` conflicts, debug overlay |
| [`:mock-server`](mock-server/) | Reference REST backend + conflict simulation |
| [`:backend-starter`](backend-starter/) / [`:backend-starter-spring`](backend-starter-spring/) | Ktor / Spring starters |
| [`:sample-ios-shared`](sample-ios-shared/) + [`:ios-sample`](ios-sample/) | iOS path |
| [`:sample-desktop`](sample-desktop/) | JVM desktop |
| [`:sample-web`](sample-web/) | Browser (monorepo-only) |

---

## Related: ForgeNav

| Concern | Library |
|---------|---------|
| Outbox, transports, conflict store, push/pull | **SyncForge** (this repo) |
| Routes, backstack, MVI, optimistic UI, sync banners | [ForgeNav](https://github.com/Arsenoal/forgenav) |

---

## Development

Contributing and local verification: [CONTRIBUTING.md](CONTRIBUTING.md).

```bash
git clone https://github.com/Arsenoal/syncforge.git
cd syncforge
./gradlew verifyReleaseSignOff
```

---

## License

[Apache License, Version 2.0](LICENSE)
