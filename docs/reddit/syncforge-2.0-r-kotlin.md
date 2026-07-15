# r/Kotlin

**Suggested title:**

```text
SyncForge 2.0 — offline-first sync library in pure Kotlin (outbox + conflicts), multiplatform, on Maven Central
```

**Body:**

```text
Hi r/Kotlin,

**SyncForge 2.0.0** is out: a Kotlin-first offline sync engine (KMP, but the API is idiomatic Kotlin end-to-end).

**What it is**
- Local mutations go through a **durable outbox** (`enqueueChange` + `sync` / push / pull)
- **Conflict strategies** per entity type: LWW, defer-to-user, field merge, `gitLike` three-way merge, CRDT field helpers
- **Pluggable transport**: default REST/Ktor, or GraphQL / Supabase / Firebase / BYO `SyncTransport`
- Your domain model stays yours — you keep Room (or any store); SyncForge doesn’t own your schema

**Why 2.0 matters**
- Full artifact set on **Maven Central** under `studio.syncforge` (core + optional modules + Gradle plugin + version catalog)
- Several APIs graduated **stable** (no `@OptIn` for EntityStore / transport / gitLike / CRDT paths that people actually use)
- Consumer pin is the version catalog, not a BOM:

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
syncManager = SyncForge.android(context) {
    baseUrl("https://api.example.com")
    registry(SyncForgeHandlers.registry(taskDao))
}

syncManager.enqueueChange(Change.create("tasks", task))
syncManager.sync()
```

Also: Coroutines/Flow status stream, KSP helpers for entities, Spring starter for self-hosting, OpenTelemetry bridge, rate-limit/backoff policies.

**Not** a Firebase replacement by force — you can self-host, use BaaS adapters, or run offline-only with a local outbox.

Repo: https://github.com/Arsenoal/syncforge  
Central: https://central.sonatype.com/namespace/studio.syncforge  
Changelog 2.0.0: https://github.com/Arsenoal/syncforge/blob/main/CHANGELOG.md

Would love API design critique from people who’ve built their own outbox/sync layers.

Apache 2.0
```
