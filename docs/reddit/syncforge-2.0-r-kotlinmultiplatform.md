# r/KotlinMultiplatform  
*(subreddit is usually r/KotlinMultiplatform — note spelling)*

**Suggested title:**

```text
SyncForge 2.0 — offline-first sync for KMP (Android / iOS / JVM / macOS) on Maven Central
```

**Body:**

```text
Hi KMP folks,

**SyncForge 2.0.0** is on Maven Central: multiplatform offline-first sync with a shared commonMain API and expect/actual only where the platform forces it.

**Model**
- commonMain: `SyncManager`, outbox contract, conflict policy, transports
- Android: `SyncForge.android { }` + Room/WorkManager-friendly wiring
- iOS / desktop / macOS: stable DSLs (`SyncForge.ios` / `desktop` / `macos`) + samples
- Your entities implement a small `SyncedEntity` contract; store can be Room, in-memory, or BYO `EntityStore`

**2.0 distribution**
- Group: `studio.syncforge`
- Pin with the catalog: `studio.syncforge:syncforge-catalog:2.0.0`
- Published: core KMP modules, optional network/transports/integrations, `studio.syncforge.android` plugin
- Browser JS sample exists in-repo (`SyncForge.web`); browser artifacts are still monorepo/local for now
- iOS: KMP frameworks today; SPM/XCFramework packaging is the next packaging step

**Shared code sketch**

```kotlin
// commonMain
syncManager.enqueueChange(Change.create("tasks", task))
syncManager.sync()

// status for UI
syncManager.status // StateFlow<SyncStatus>
```

**Conflicts across platforms**
Same policy DSL everywhere, e.g. `gitLike { }` / `crdt { }` / defer-to-user — including Compose Multiplatform conflict UI patterns for shared UI modules.

**Repo & samples**
- https://github.com/Arsenoal/syncforge
- Android sample, iOS sample, desktop sample, mock server, Spring starter
- Docs: GETTING_STARTED, IOS_SETUP, DESKTOP_SETUP, CONFLICT_RESOLUTION

If you’ve tried PowerSync / custom SQLDelight outboxes / hand-rolled delta sync, I’d love a comparison of pain points — especially around iOS lifecycle and conflict UX.

Companion UI layer (optional): ForgeNav for type-safe nav + offline-aware MVI — https://github.com/Arsenoal/forgenav

Apache 2.0 · Kotlin 2.1+ · Coordinates under studio.syncforge
```
