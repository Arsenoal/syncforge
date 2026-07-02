# SyncForge

A lightweight, offline-first sync library for Android (Kotlin Multiplatform).

Your app entities live in Room (or your own store on iOS). SyncForge queues mutations in a
SQLDelight outbox, syncs with your backend through a pluggable transport, and handles conflicts,
Compose status observation, and an in-app debug console.

**Current version:** `0.9.0-rc.1`

> SyncForge is a **pre-1.0** library. Android is the reference platform; iOS, JVM desktop, and
> native macOS targets are available. First Maven Central release: `0.9.0-rc.1` — see
> [docs/MAVEN_PUBLISH.md](docs/MAVEN_PUBLISH.md).

---

## See it in action

The `:sample` app is a multi-tab Tasks / Notes / Tags demo. Run it against the mock server in two terminals:

```bash
./gradlew :mock-server:run          # Terminal 1
./gradlew :sample:installDebug      # Terminal 2 (emulator → http://10.0.2.2:8080)
```

```mermaid
sequenceDiagram
    participant UI as Your UI
    participant Room as Room (your DB)
    participant SF as SyncForge
    participant API as Your backend

    UI->>Room: Save task (instant)
    UI->>SF: enqueueChange()
    Note over SF: Outbox persists — survives app kill

    alt Offline
        SF-->>UI: Status: Offline / Pending
    end

    UI->>SF: sync()
    SF->>API: POST /sync/push
    SF->>API: GET /sync/pull
    API-->>SF: deltas
    SF->>Room: Apply remote changes

    opt Conflict (same task edited twice)
        SF-->>UI: Conflict chip + Resolve sheet
        UI->>SF: resolveConflict(keepLocal | keepRemote)
    end

    SF-->>UI: Status: Synced
```

### What the demo shows

| Scenario | What to do | What you see |
|----------|------------|--------------|
| **1. Offline-first** | Add a task with airplane mode on | Task appears in Room immediately; status shows pending / offline |
| **2. Sync** | Turn network on → tap **Sync** | Push + pull run; row shows **Synced**; outbox drains |
| **3. Conflict** | Sync a task → edit it on the server (mock `/dev` routes) → edit locally → **Sync** again | **Conflict** chip appears; tap **Resolve** to pick local or server version |

**Debug console (debug builds):** tap the **SF** button on the Tasks tab to inspect the outbox, sync health, events, and open conflicts.

**iOS:** same flows in SwiftUI — `open ios-sample/SyncForgeTasks.xcodeproj` (see [iOS setup](docs/IOS_SETUP.md)).

---

## New here?

**[Getting Started →](docs/GETTING_STARTED.md)** — zero to a working offline-first app in under 10 minutes.

Then explore [Recipes](docs/RECIPES.md) for merge logic, conflict UI, and the debug console.

---

## What you get

| Feature | Description |
|---------|-------------|
| **Optimistic writes** | Local DB updates instantly; rollback on server rejection |
| **Persistent outbox** | Survives process death; retries with exponential backoff |
| **Push / pull / sync** | Full cycle or individual operations |
| **`SyncForge.android { }`** | ~10-line setup with Android defaults |
| **KSP codegen** | `@SyncForgeEntity` → handlers + `SyncForgeHandlers` registry |
| **Conflict strategies** | LWW, merge, defer-to-user — per entity type |
| **Debug console** | In-app outbox/conflict/event inspector (debug builds) |
| **Compose helpers** | Status observation, conflict chip, resolution sheet |
| **KMP platforms** | iOS (`SyncForge.ios`), JVM desktop (`SyncForge.desktop`), native macOS (`SyncForge.macos`) |
| **SQLDelight persistence** | Default on all platforms since 0.6.0; automatic Room → SQLDelight migration on Android upgrade |

[Full capabilities table →](docs/ROADMAP.md#what-the-library-can-do-today-v060)

---

## Minimal setup

```kotlin
syncManager = SyncForge.android(this) {
    baseUrl("https://api.example.com")
    registry(SyncForgeHandlers.registry(taskDao))
    schedulePeriodicSyncOnStart()
}
```

```kotlin
syncManager.enqueueChange(Change.create("tasks", task))
syncManager.sync()
```

Step-by-step walkthrough with entity, DAO, repository, and Compose: **[Getting Started](docs/GETTING_STARTED.md)**.

---

## Documentation

| Guide | Description |
|-------|-------------|
| **[Getting Started](docs/GETTING_STARTED.md)** | 10-minute integration walkthrough |
| **[Recipes](docs/RECIPES.md)** | Merge, deferToUser, debug console, status observation |
| **[Conflict Resolution](docs/CONFLICT_RESOLUTION.md)** | Strategies, lifecycle, decision guide |
| **[Best Practices](docs/BEST_PRACTICES.md)** | Entity design, performance, error handling |
| [Module reference](docs/MODULES.md) | Package-by-package API |
| [REST API contract](docs/REST_API.md) | Backend endpoints |
| [Android setup](docs/ANDROID_SETUP.md) | SQLDelight default, legacy Room opt-in, migration |
| [iOS setup](docs/IOS_SETUP.md) | `SyncForge.ios { }` configuration |
| [Desktop setup](docs/DESKTOP_SETUP.md) | `SyncForge.desktop { }` / `SyncForge.macos { }` |
| [KMP migration](docs/KMP_MIGRATION.md) | iOS/SQLDelight transition plan |
| [Roadmap](docs/ROADMAP.md) | Limitations and future phases |
| [Launch playbook](docs/SyncForge-GitHub-Launch-Playbook.docx) | 1.0 soak, Maven Central, GitHub growth |
| [Changelog](CHANGELOG.md) | Release history |

[Documentation index →](docs/README.md)

---

## Project structure

| Module | Description |
|--------|-------------|
| `:syncforge` | Library — all public API; SQLDelight repo impls in `syncPersistenceMain` |
| `:syncforge-annotations` | `@SyncForgeEntity` / `@SyncForgeDao` |
| `:syncforge-ksp` | KSP processor |
| `:syncforge-persistence` | SQLDelight schemas + platform drivers (KMP) |
| `:syncforge-android-deps` | Transitive Android runtime deps (Room, serialization, WorkManager) |
| `:syncforge-gradle-plugin` | `dev.syncforge.android` — KSP + serialization setup for app modules |
| `:syncforge-bom` | Maven BOM — aligned versions for all library artifacts |
| `:syncforge-server` | Shared Ktor sync routes and `SyncStore` contract |
| `:backend-starter` | Minimal reference backend (production starter) |
| `:mock-server` | JVM Ktor dev server (+ conflict demo routes) |
| `:sample` | Android Compose Tasks demo |
| `:sample-ios-shared` | iOS sample framework (`IosSampleController`) |
| `ios-sample/` | SwiftUI Xcode app wired to `:sample-ios-shared` |

---

## Requirements

- Android minSdk 24 · Kotlin 2.1+ · JVM 17

---

## Advanced setup

Low-level `SyncForge.create()` / `createWithRetry()` and `SyncForge.builder { }` remain
available for custom wiring and tests. See [Module reference](docs/MODULES.md).

---

## License

SyncForge is licensed under the [Apache License, Version 2.0](LICENSE).

You may use, modify, and distribute this library in open-source and commercial
applications without copyleft obligations. See [LICENSE](LICENSE) for the full text.