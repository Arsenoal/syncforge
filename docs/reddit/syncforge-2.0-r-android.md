# r/androiddev (or r/android)

**Suggested title:**

```text
SyncForge 2.0 — offline-first sync for Android (Room-friendly outbox) now on Maven Central
```

**Body:**

```text
Hey Android folks,

I open-sourced **SyncForge**, an offline-first sync library for Kotlin Multiplatform, and **2.0.0 is on Maven Central**.

**Problem it solves**
Most “sync” samples still assume online-first. Real apps need:
- local writes that never block the UI
- a durable outbox that survives process death
- conflict handling that isn’t “last write wins or cry”
- your own Room schema (not a proprietary DB)

**How SyncForge fits Android**
- Your entities stay in **your** Room DB (or a custom EntityStore)
- SyncForge owns a separate **outbox + conflict store**
- `enqueueChange(...)` → optimistic local write → push/pull when online
- WorkManager-friendly periodic sync on Android
- Compose helpers for sync status / debug / conflicts

**2.0.0 highlights**
- First full multiplatform publish on Central since 1.1 (Android + iOS + JVM + macOS artifacts)
- Stable `gitLike` / CRDT-style merge strategies (no OptIn for the common path)
- Version catalog: pin everything with `studio.syncforge:syncforge-catalog:2.0.0`
- Optional transports: REST/Ktor (default), GraphQL, Supabase, Firebase
- Spring Boot starter if you want a self-hosted backend
- Observability: tracing hooks, SyncHealth metrics, conflict audit export

**Quick start (Android)**

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
// app
plugins { alias(syncforge.plugins.syncforge.android) }
dependencies { implementation(syncforge.core) }

syncManager = SyncForge.android(this) {
    baseUrl("https://api.example.com")
    registry(SyncForgeHandlers.registry(taskDao))
    schedulePeriodicSyncOnStart()
}

syncManager.enqueueChange(Change.create("tasks", task))
syncManager.sync()
```

**Links**
- GitHub: https://github.com/Arsenoal/syncforge
- Maven: https://central.sonatype.com/namespace/studio.syncforge
- Getting started: https://github.com/Arsenoal/syncforge/blob/main/docs/GETTING_STARTED.md
- Upgrade 1.1 → 2.0: https://github.com/Arsenoal/syncforge/blob/main/docs/UPGRADE_1_1_TO_2_0.md

If you’re building notes/tasks/field apps that must work offline, I’d love feedback (or harsh reviews). Happy to answer Room / WorkManager / conflict strategy questions in the comments.

Apache 2.0 · Kotlin 2.1+
```
