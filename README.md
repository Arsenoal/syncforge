# SyncForge

**Offline-first sync for Kotlin Multiplatform** — Android, iOS, JVM desktop, and native macOS.

![Kotlin](https://img.shields.io/badge/kotlin-2.1+-7F52FF?logo=kotlin&logoColor=white)
![Maven Central](https://img.shields.io/maven-central/v/studio.syncforge/syncforge?color=0D7377)
![License](https://img.shields.io/github/license/Arsenoal/syncforge)
[![CI](https://github.com/Arsenoal/syncforge/actions/workflows/ci.yml/badge.svg)](https://github.com/Arsenoal/syncforge/actions/workflows/ci.yml)

![Android](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![iOS](https://img.shields.io/badge/platform-iOS-000000?logo=apple&logoColor=white)
![Desktop](https://img.shields.io/badge/platform-JVM%20desktop-007396?logo=openjdk&logoColor=white)
![macOS](https://img.shields.io/badge/platform-macOS-000000?logo=apple&logoColor=white)

**v2.0.0** on [Maven Central](https://central.sonatype.com/namespace/studio.syncforge) · [Release notes](CHANGELOG.md#200---2026-07-09)

Your app keeps its own database (Room on Android, or any store you choose). SyncForge owns a
separate outbox and conflict store, then push/pulls over a pluggable transport. Local writes are
instant; sync survives offline use and app restarts.

> **UI layer:** For type-safe multiplatform navigation, MVI, and sync-aware Compose chrome
> (optimistic UI, pending badges, conflict dialogs), see
> **[ForgeNav](https://github.com/Arsenoal/forgenav)** (`studio.forgenav`).

---

## See it in action

<p align="center">
  <img src="docs/images/syncforge-demo.gif" alt="SyncForge demo: add task, sync, conflict resolution, clear local DB, pull from server" width="360" style="max-width: 360px; border-radius: 16px; border: 1px solid #e0e0e0;" />
</p>

```bash
./gradlew :mock-server:run          # Terminal 1
./gradlew :sample:installDebug      # Terminal 2 — emulator uses http://10.0.2.2:8080
```

Tap **Sync** in the sample app, trigger a **conflict**, or clear local data and pull from the server.
Debug builds show an **SF** overlay for outbox and sync health.

---

## Why SyncForge

**Offline-first** — Users edit locally; mutations queue in a durable outbox until the network returns.

**Your data stays yours** — Entity tables live in your Room DB or custom `EntityStore`. SyncForge does not replace your schema.

**Conflict-aware** — Per-entity strategies: last-write-wins, user resolution, field merge, `gitLike` three-way merge, CRDT fields.

**Pluggable backend** — Default REST/Ktor transport, or GraphQL, Supabase, Firebase, and custom `SyncTransport` adapters.

**Optional sync** — Ship offline-only, self-host a backend, or point at any server implementing the [push/pull contract](docs/REST_API.md).

---

## Quick start

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

// Repository
syncManager.enqueueChange(Change.create("tasks", task))
syncManager.sync()
```

**Full walkthrough (~10 min):** [Getting Started](docs/GETTING_STARTED.md)

---

## Platforms

| Platform | Entry point | Setup guide | Sample |
|----------|-------------|-------------|--------|
| **Android** | `SyncForge.android { }` | [Android setup](docs/ANDROID_SETUP.md) | [`:sample`](sample/) |
| **iOS** | `SyncForge.ios { }` | [iOS setup](docs/IOS_SETUP.md) | [`:ios-sample`](ios-sample/) |
| **JVM desktop** | `SyncForge.desktop { }` | [Desktop setup](docs/DESKTOP_SETUP.md) | [`:sample-desktop`](sample-desktop/) |
| **macOS native** | `SyncForge.macos { }` | [Desktop setup](docs/DESKTOP_SETUP.md) | — |
| **Browser** | `SyncForge.web { }` *(experimental)* | [Web setup](docs/WEB_SETUP.md) | [`:sample-web`](sample-web/) |

Gradle snippets, DSL options, and platform-specific wiring are in each setup guide — not duplicated here.

---

## Documentation

| | |
|---|---|
| **All features + samples** | [Feature catalog](docs/FEATURES.md) |
| **Copy-paste recipes** | [Recipes](docs/RECIPES.md) |
| **API reference** | [Modules](docs/MODULES.md) |
| **Backend contract** | [REST API](docs/REST_API.md) |
| **Conflicts** | [Conflict resolution](docs/CONFLICT_RESOLUTION.md) |
| **Navigation + MVI (Compose)** | [ForgeNav](https://github.com/Arsenoal/forgenav) |
| **Everything else** | [docs/README.md](docs/README.md) |

### Sample apps

| Module | What it proves |
|--------|----------------|
| [`:sample`](sample/) | Multi-entity sync, `gitLike` conflicts, debug overlay, Compose UI |
| [`:mock-server`](mock-server/) | Reference backend + conflict simulation endpoints |
| [`:backend-starter-spring`](backend-starter-spring/) | Spring Boot + JDBC store |

---

## Related: ForgeNav

SyncForge is the **sync engine**. For multiplatform navigation and sync-aware UI state, use:

**[ForgeNav](https://github.com/Arsenoal/forgenav)** — navigation + offline-first MVI for Compose Multiplatform (`studio.forgenav`).

| Concern | Library |
|---------|---------|
| Outbox, transports, conflict store, push/pull | **SyncForge** (this repo) |
| Routes, backstack, MVI, optimistic UI, sync banners | [ForgeNav](https://github.com/Arsenoal/forgenav) |

---

## Development

Want to contribute or run the repo locally? See [CONTRIBUTING.md](CONTRIBUTING.md).

```bash
git clone https://github.com/Arsenoal/syncforge.git
cd syncforge
./gradlew verifyReleaseSignOff
```

---

## License

[Apache License, Version 2.0](LICENSE)