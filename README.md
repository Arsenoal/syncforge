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

<p align="center">
  <img src="docs/images/syncforge-demo.gif" alt="SyncForge demo: add task, sync, clear local DB, pull from server" width="360" style="max-width: 360px; border-radius: 16px; border: 1px solid #e0e0e0;" />
</p>

<p align="center">
  <sub>Offline write → sync → <strong>clear local DB</strong> (empty Room) → pull from server · <a href="docs/images/README.md">re-record</a></sub>
</p>

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
    participant Auth as Auth (TokenStore)
    participant API as Your backend

    opt Built-in auth (optional)
        UI->>SF: login(email, password)
        SF->>API: POST /auth/login
        API-->>SF: access_token + refresh_token
        SF->>Auth: persist tokens
        SF-->>UI: authState → logged in
    end

    UI->>Room: Save task (instant)
    UI->>SF: enqueueChange()
    Note over SF: Outbox persists — survives app kill

    alt Offline
        SF-->>UI: Status: Offline / Pending
    end

    UI->>SF: sync()
    SF->>API: POST /sync/push (Bearer token)
    alt HTTP 401 — token expired
        SF->>API: POST /auth/refresh
        API-->>SF: new tokens
        SF->>Auth: update tokens
        SF->>API: retry push
    end
    SF->>API: GET /sync/pull (Bearer token)
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
| **3. Empty local DB** | Tap **Clear local DB** in the demo panel → **Sync** | Room wiped; tasks disappear; pull restores data from mock-server |
| **4. Conflict** | Sync a task → tap **Server edit** → edit locally → **Sync** again | **Conflict** chip appears; tap **Resolve** to pick local or server version |

**Debug console (debug builds):** tap the **SF** button on the Tasks tab to inspect the outbox, sync health, events, and open conflicts.

**iOS:** same flows in SwiftUI — `open ios-sample/SyncForgeTasks.xcodeproj` (see [iOS setup](docs/IOS_SETUP.md)).

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

Auth setup: **[Auth API](docs/AUTH_API.md)** · runnable backend: `./gradlew :backend-starter:run`

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