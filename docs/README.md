# SyncForge documentation

**Version:** `0.9.0-rc.4` · **Status:** release candidate (pre-1.0) · [Maven Central](https://central.sonatype.com/namespace/studio.syncforge)

SyncForge is an offline-first sync library for Android with Kotlin Multiplatform targets for
iOS, JVM desktop, and native macOS. Your app entities live in Room (Android) or your own
store; SyncForge owns a separate SQLDelight outbox and conflict database. Mutations go through
an outbox; push/pull talk to your backend over a pluggable transport.

---

## Start here

| I want to…                                     | Read this                                                                      |
|------------------------------------------------|--------------------------------------------------------------------------------|
| **Get a working app in ~10 minutes**           | [Getting Started](GETTING_STARTED.md)                                          |
| **Copy-paste solutions for common tasks**      | [Recipes](RECIPES.md)                                                          |
| **Understand and configure conflict handling** | [Conflict Resolution](CONFLICT_RESOLUTION.md)                                  |
| **Design entities and choose strategies**      | [Best Practices](BEST_PRACTICES.md)                                            |
| **Set up SyncForge on Android**                | [Android Setup](ANDROID_SETUP.md)                                              |
| **Set up SyncForge on iOS**                    | [iOS Setup](IOS_SETUP.md)                                                      |
| **Set up SyncForge on desktop (JVM)**          | [Desktop Setup](DESKTOP_SETUP.md)                                              |
| **Understand the KMP migration plan**          | [KMP Migration](KMP_MIGRATION.md)                                              |
| **Look up every public type**                  | [Module Reference](MODULES.md)                                                 |
| **Implement the backend HTTP contract**        | [REST API](REST_API.md)                                                        |
| **Add login/register (built-in auth)**         | [Auth API](AUTH_API.md)                                                        |
| **See what's planned next**                    | [Roadmap](ROADMAP.md)                                                          |
| **Track 1.0 release blockers (P0)**            | [SyncForge-1.0-P0.docx](SyncForge-1.0-P0.docx)                                 |
| **Launch playbook (1.0 soak, GitHub growth)**  | [SyncForge-GitHub-Launch-Playbook.docx](SyncForge-GitHub-Launch-Playbook.docx) |
| **Record README demo GIF**                     | [docs/images/README.md](images/README.md)                                      |
| **Track release changes**                      | [Changelog](../CHANGELOG.md)                                                   |

---

## Documentation map

```
docs/
├── README.md                 ← You are here (index + learning paths)
├── GETTING_STARTED.md        ← Zero → working offline-first app (~10 min)
├── ANDROID_SETUP.md          ← Android DSL, SQLDelight default, Room migration
├── IOS_SETUP.md              ← iOS DSL, SQLDelight defaults, Swift integration
├── DESKTOP_SETUP.md          ← JVM desktop + native macOS DSL
├── RECIPES.md                ← How-to: merge, deferToUser, debug, observe status
├── CONFLICT_RESOLUTION.md      ← Strategies, lifecycle, Compose UI, decision guide
├── BEST_PRACTICES.md         ← Entity design, strategy choices, performance
├── KMP_MIGRATION.md          ← Room → SQLDelight, iOS targets, expect/actual plan
├── MODULES.md                ← Package-by-package API reference
├── REST_API.md               ← Backend push/pull contract
├── AUTH_API.md               ← Built-in register/login/refresh (Android flow + diagram)
├── ROADMAP.md                ← Phases, limitations, future work
├── SyncForge-1.0-P0.docx     ← P0 checklist
├── SyncForge-GitHub-Launch-Playbook.docx  ← 1.0 soak, Maven Central, GitHub growth playbook
└── images/                   ← README demo GIF (+ recording guide)
```

---

## Learning paths

### Path A — First integration (recommended)

1. [Getting Started](GETTING_STARTED.md) — entity, KSP, `SyncForge.android { }`, first sync
2. [Recipes → Observe sync status](RECIPES.md#observe-sync-status-in-compose) — status banner
3. [REST API](REST_API.md) — wire your backend (or use `:mock-server` locally)
4. [Best Practices → Entity design](BEST_PRACTICES.md#entity-design)

### Path B — Conflict-aware apps

1. [Conflict Resolution](CONFLICT_RESOLUTION.md) — when conflicts happen, strategy overview
2. [Recipes → Custom merge](RECIPES.md#custom-merge-with-merge--) — field-level merges
3. [Recipes → deferToUser in Compose](RECIPES.md#handle-defertouser-conflicts-in-compose) — user resolution UI
4. [Best Practices → Choosing a strategy](BEST_PRACTICES.md#choosing-a-conflict-strategy)

### Path C — Debugging & QA

1. [Recipes → Debug console](RECIPES.md#use-the-in-app-debug-console) — `SyncDebugLauncher`
2. [Module Reference → dev.syncforge.debug](MODULES.md#devsyncforgedebug--developer-observability)
3. Run the `:sample` app with `:mock-server` — conflict demo walkthrough in [Getting Started](GETTING_STARTED.md#try-the-conflict-demo-optional)

---

## Sample app

The `:sample` module is the canonical reference implementation:

```bash
./gradlew :mock-server:run          # Terminal 1
./gradlew :sample:installDebug      # Terminal 2 (emulator)
```

Key files:

| File                                 | What it demonstrates                               |
|--------------------------------------|----------------------------------------------------|
| `sample/.../tasks/TaskEntity.kt`     | `@SyncForgeEntity` + `SyncedEntity`                |
| `sample/.../notes/NoteEntity.kt`     | Second entity type + KSP handler                   |
| `sample/.../SampleApplication.kt`    | Single `SyncManager`, multi-entity `conflicts { }` |
| `sample/.../navigation/SampleApp.kt` | Compose Navigation across Tasks / Notes / Tags     |
| `sample/.../tasks/TaskRepository.kt` | `enqueueChange` + `sync()`                         |
| `sample/.../tasks/TasksViewModel.kt` | Status, conflicts, resolution                      |
| `sample/.../tasks/TasksScreen.kt`    | Conflict sheet, server edit/delete dev actions     |
| `sample/.../notes/NotesScreen.kt`    | Second entity + optional `tagId` FK to tags        |
| `sample/.../navigation/SampleApp.kt` | Bottom nav, SF debug overlay, demo log panel       |

### Demo scenarios (manual)

| Tab            | What it proves                                                                                        |
|----------------|-------------------------------------------------------------------------------------------------------|
| **Tasks**      | `deferToUser()` conflicts; mock-server **Server edit** / **Server delete**; local delete + tombstones |
| **Notes**      | `lastWriteWins()`; optional relationship to tags (app-level FK)                                       |
| **Tags**       | Third entity type in the same `SyncForgeHandlers.registry`                                            |
| **Demo panel** | Clear local Room + pull restore; live outbox/sync narration (debug builds)                            |

The sample satisfies the multi-entity proof in the 1.0 P0 checklist; run `./gradlew androidE2e` (Android) or `./gradlew iosE2e` (macOS/Xcode) locally, or see CI `android-e2e` / `ios-e2e` jobs.

---

## Contributing to docs

When adding a feature, update in this order:

1. [CHANGELOG.md](../CHANGELOG.md)
2. Relevant guide (Getting Started / Recipes / Conflict Resolution)
3. [MODULES.md](MODULES.md) API reference
4. [README.md](../README.md) capabilities table (one line max per feature)