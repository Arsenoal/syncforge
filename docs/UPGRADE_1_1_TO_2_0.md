# Upgrade guide: 1.1.0 → 2.0.0

How to move from the last Maven Central release (**`1.1.0`**) to **`2.0.0`**, which publishes the full **1.2–1.6** backlog in one coordinated rollout.

**Scope (locked):** see [ROADMAP_1_0_TO_2_0.md § 2.0.0 locked scope](ROADMAP_1_0_TO_2_0.md#200-locked-scope-july-2026).

---

## Summary

| Area | Breaking? | Action |
|------|-----------|--------|
| Core sync loop (`SyncManager`, outbox, push/pull) | **No** | Bump coordinates; no API migration |
| `conflicts { }` — LWW, merge, defer, alwaysLocal/Remote | **No** | Unchanged |
| `SyncForge.android { }` / stable platform DSLs | **No** | Unchanged (iOS/desktop/macOS stable since 1.3) |
| Version alignment — BOM → catalog | **Yes (distribution)** | Replace `platform("syncforge-bom:…")` with catalog import |
| `syncforge-bom` artifact | **Not published at 2.0** | Historical `1.1.0` BOM on Central still resolves; new work uses catalog |
| Optional modules | **No** | Still not transitive — add explicit deps (now via catalog aliases) |
| `gitLike { }` / `crdt { }` | **No** (graduation) | `@OptIn` no longer required at 2.0.0; remove if desired |
| REST `/sync/push` + `/sync/pull` | **No** | Contract frozen at v1 |
| Browser `SyncForge.web { }` | **N/A on Central** | Not in Maven Central artifact set; monorepo-only |
| Op-log / REST v2 | **N/A** | Deferred to 2.1+ |

**Bottom line:** Most apps bump the version catalog (or coordinates) and add optional modules they need. No server or handler changes required unless you adopt new 1.2–1.6 features.

---

## Who should upgrade

| You are on… | Upgrade path |
|-------------|--------------|
| **`1.1.0` from Maven Central** | This guide — bump to `2.0.0` after Central sync |
| **`1.0.0` from Maven Central** | Bump to `2.0.0`; review [CHANGELOG [1.1.0]](CHANGELOG.md#110---2026-07-07) wire-up changes first |
| **`main` / git / `publishToMavenLocal`** | Already on 1.2–1.6 features; align pins to `2.0.0` at release cut |
| **Monorepo tags `v1.2.0`–`v1.6.0`** | Milestone markers only; Central consumers should use **`2.0.0`** |

---

## Step 1 — Version catalog (recommended)

Replace BOM-based alignment with the published catalog.

**Before (`1.1.0` BOM):**

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}

// app/build.gradle.kts
dependencies {
    implementation(platform("studio.syncforge:syncforge-bom:1.1.0"))
    implementation("studio.syncforge:syncforge")
}
```

**After (`2.0.0` catalog):**

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("syncforge") {
            from("studio.syncforge:syncforge-catalog:2.0.0")
        }
    }
}

// app/build.gradle.kts
plugins {
    alias(libs.plugins.kotlinAndroid)
    alias(syncforge.plugins.syncforge.android)
}

dependencies {
    implementation(syncforge.core)
}
```

Optional modules use the same version pin:

```kotlin
implementation(syncforge.transport.supabase)
implementation(syncforge.integration.koin)
implementation(syncforge.integration.opentelemetry)
```

Full alias table: [syncforge-catalog/README.md](../syncforge-catalog/README.md).

---

## Step 2 — Plugin and coordinates

| Component | 1.1.0 | 2.0.0 |
|-----------|-------|-------|
| Android plugin | `id("studio.syncforge.android") version "1.1.0"` | `alias(syncforge.plugins.syncforge.android)` from catalog |
| Core library | `studio.syncforge:syncforge:1.1.0` | `syncforge.core` or `studio.syncforge:syncforge:2.0.0` |
| KSP processor | Transitive via plugin | Unchanged |
| Catalog | Not required | `studio.syncforge:syncforge-catalog:2.0.0` |

After publish, verify resolution:

```bash
curl -sI "https://repo1.maven.org/maven2/studio/syncforge/syncforge-catalog/2.0.0/syncforge-catalog-2.0.0.toml" | head -1
curl -sI "https://repo1.maven.org/maven2/studio/syncforge/syncforge/2.0.0/syncforge-2.0.0.pom" | head -1
```

Expect `HTTP/2 200` after Central sync (~15–60 minutes post portal Publish).

---

## Step 3 — Optional modules (new since 1.1.0)

These ship on Maven Central at **`2.0.0`** but remain **non-transitive** — declare only what you use.

| Catalog alias | Module | Since |
|---------------|--------|-------|
| `transport-core` | BaaS `SyncDeltaStore` + `DeltaStoreSyncTransport` | 1.4.0 |
| `transport-supabase` | Supabase `SyncDeltaStore` | 1.4.0 |
| `transport-firebase` | Firebase `SyncDeltaStore` | 1.4.0 |
| `transport-graphql` | GraphQL `SyncTransport` | 1.4.0 |
| `integration-opentelemetry` | OpenTelemetry `SyncTracer` bridge | 1.5.0 |

Existing 1.1 optional modules unchanged: `network-ktor`, `store-room`, `store-inmemory`, `integration-koin`, `integration-hilt`.

Guides: [RECIPES.md](RECIPES.md), [CUSTOM_TRANSPORT.md](CUSTOM_TRANSPORT.md), [TRACING.md](TRACING.md).

---

## Step 4 — Conflict strategies (1.2.0+)

New strategies are **opt-in** — existing `conflicts { }` blocks keep working.

| Feature | 1.1.0 | 2.0.0 |
|---------|-------|-------|
| `lastWriteWins()`, `merge { }`, `deferToUser()`, `alwaysLocal()` / `alwaysRemote()` | ✅ | ✅ unchanged |
| `gitLike { }` three-way merge | — | ✅ stable (no `@OptIn` at 2.0.0) |
| `crdt { }` field CRDT | — | ✅ stable (no `@OptIn` at 2.0.0) |
| `ConflictStrategyKind` + `updateConflictPolicy()` | — | ✅ additive |
| Merge-base store | — | ✅ automatic when using `gitLike` |

If you already opted in to `gitLike` / `crdt` on a monorepo build, you may remove `@OptIn(ExperimentalSyncForgeApi::class)` after upgrading to **`2.0.0`**.

Details: [CONFLICT_RESOLUTION.md](CONFLICT_RESOLUTION.md).

---

## Step 5 — Platform-specific notes

### Android

- **Built-in auth**, encrypted `TokenStore`, DataStore cursor — unchanged since 1.1.0.
- **New (optional):** SyncHealth dashboard, tracing hooks, rate limiting — see [TRACING.md](TRACING.md), [RATE_LIMITING.md](RATE_LIMITING.md).
- **SQLDelight outbox** — still default; no Room persistence path.

### iOS / macOS

- **`SyncForge.ios { }` / `SyncForge.macos { }`** — stable since 1.3.0; included in 2.0.0 KMP artifacts on Central.
- **SPM / XCFramework** — **not** required for 2.0.0; deferred to **2.0.1**. Continue with KMP frameworks per [IOS_SETUP.md](IOS_SETUP.md).

### Desktop (JVM)

- **`SyncForge.desktop { }`** — stable since 1.3.0; `syncforge-jvm` target on Central.

### Web (browser)

- **`SyncForge.web { }`** — **not published to Maven Central** at 2.0.0. Use git clone, composite build, or `publishToMavenLocal`. See [WEB_SETUP.md](WEB_SETUP.md).

---

## Step 6 — Backend / REST contract

**No changes required.** `2.0.0` keeps REST v1:

- `POST /sync/push`
- `GET /sync/pull`

Optional GraphQL or BaaS adapters are **client-side** `SyncTransport` swaps — server semantics stay the same. See [REST_API.md](REST_API.md).

REST v2 and op-log sync are **deferred to 2.1+**.

---

## Step 7 — Data migration

| Data | Migration |
|------|-----------|
| Auth tokens (Android/iOS) | Automatic on first launch (same as 1.0 → 1.1) |
| Pull cursor (Android DataStore) | Automatic |
| SQLDelight outbox / conflicts | Forward-compatible schema; no manual step |
| Room → SQLDelight (legacy) | Already handled by `RoomToSqlDelightMigrator` on Android |

No wipe or re-sync required for a straight version bump.

---

## What is **not** in 2.0.0

| Item | Status |
|------|--------|
| `studio.syncforge:syncforge-bom` at `2.0.0` | Not published — use catalog |
| Browser `js` artifacts on Central | Monorepo-only |
| `:syncforge-crdt` op-log channel | Deferred 2.1+ |
| REST API v2 | Deferred 2.1+ |
| iOS SPM binary | Target 2.0.1 |
| KSP-generated DI modules | Deferred 2.1+ |

---

## Checklist

- [ ] Import `syncforge-catalog:2.0.0` in `settings.gradle.kts`
- [ ] Remove `platform("studio.syncforge:syncforge-bom:…")` if present
- [ ] Use `alias(syncforge.plugins.syncforge.android)` (or pin plugin `2.0.0`)
- [ ] Add optional catalog aliases for transports / tracing / DI as needed
- [ ] Remove `@OptIn` for `gitLike` / `crdt` if you use them (optional cleanup)
- [ ] Run app smoke tests + `./gradlew :app:compileDebugKotlin` (or your module equivalent)
- [ ] Confirm backend still speaks REST v1

---

## Related docs

- [GETTING_STARTED.md](GETTING_STARTED.md) — fresh Android setup with catalog
- [MAVEN_PUBLISH.md](MAVEN_PUBLISH.md) — maintainer publish flow for `v2.0.0`
- [RELEASE.md](RELEASE.md) — tag, GitHub Release, Central portal steps
- [CHANGELOG.md](../CHANGELOG.md) — `[1.2.0]` through `[1.6.0]` feature lists
- [ROADMAP_1_0_TO_2_0.md § 2.0.0 sign-off](ROADMAP_1_0_TO_2_0.md#200-sign-off-checklist)