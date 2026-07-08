# Conflict Resolution (v2 — 1.2)

SyncForge detects conflicts during **pull** when a remote delta arrives for an entity whose local
row is not aligned with the server. A **conflict strategy** (configured per `entityType`) decides
what happens next: auto-merge, pick a winner, or defer to the user.

This guide reflects the **1.2** strategy catalog, `:sample` reference wiring, git-like three-way
merge, outbox reconciliation, and the instrumented E2E matrix validated in CI.

---

## When does a conflict happen?

A conflict is detected when a pull delta arrives for an existing local row and **any** of these
hold ([`ConflictDetector`](../syncforge/src/commonMain/kotlin/dev/syncforge/conflict/ConflictDetector.kt)):

| Local state | Condition |
|-------------|-----------|
| `PENDING` | Local outbox has unpushed edits |
| `CONFLICT` | Row already deferred to the user |
| `SYNCED` | `localVersion != remote.serverVersion` (server advanced since last ack) |

Typical two-device scenario:

```
Device A: edits task title  →  PENDING in outbox
Device B: edits same task   →  server version bumps
Device A: pulls             →  strategy runs (auto-merge, LWW, defer, …)
```

Push rejections with `CONFLICT` are **not** the policy system — they are optimistic-concurrency
failures on stale `localVersion`. The next pull applies the configured strategy against the
server delta.

---

## Strategy catalog

Configure strategies in the `conflicts { }` block inside `SyncForge.android { }` (or
`SyncForge.ios` / `SyncForge.desktop`):

```kotlin
@OptIn(ExperimentalSyncForgeApi::class)
SyncForge.android(context) {
    conflicts {
        default(ConflictStrategies.lastWriteWins())  // optional — this is the default

        entity("notes") { alwaysRemote() }
        entity("tags") { lastWriteWins() }
        entity("tasks") { gitLike<TaskEntity> { /* threeWayMerge + fallbacks */ } }
    }
}
```

### Built-in strategies

| DSL / kind | `ConflictStrategyKind` | Resolution | User prompt? | Conflict store? |
|------------|------------------------|------------|--------------|-----------------|
| `lastWriteWins()` | `LAST_WRITE_WINS` | Newer `updatedAtMillis` wins | No | Auto-resolved only |
| `alwaysLocal()` | `ACCEPT_LOCAL` | Keep local, re-push | No | No |
| `alwaysRemote()` | `ACCEPT_REMOTE` | Server row replaces local | No | No |
| `merge { }` | `MERGE` | Custom two-way field combine | No | No |
| `gitLike { }` | `GIT_LIKE` | Three-way merge; fallback on clash | Only if fallback is `deferToUser()` | Only when deferred |
| `crdt { }` *(experimental)* | `CRDT` | Per-field CRDT merge | No | No |
| `deferToUser()` | `DEFER_TO_USER` | Stored until `resolveConflict()` | **Yes** | **Yes** |

**Default:** every entity type uses `lastWriteWins()` unless overridden.

**Runtime catalog:** `ConflictStrategies.fromKind(ConflictStrategyKind)` resolves simple kinds.
`MERGE`, `GIT_LIKE`, and `CRDT` require configured DSL blocks. Use
`syncManager.updateConflictPolicy()` to swap policies at runtime (see
[`updateConflictPolicy`](../syncforge/src/commonMain/kotlin/dev/syncforge/sync/SyncManager.kt)).

`gitLike { }` and `crdt { }` are marked `@ExperimentalSyncForgeApi` until 2.0 graduation.

---

## `:sample` per-entity matrix (reference 1.2)

The sample app wires **three entity types on one `SyncManager`** with **different strategies**
— the canonical 1.2 proof. Copy from
[`SampleConflictPolicies.kt`](../sample/src/main/kotlin/dev/syncforge/sample/conflicts/SampleConflictPolicies.kt)
or call `sampleEntityConflicts()` from [`SampleApplication.kt`](../sample/src/main/kotlin/dev/syncforge/sample/SampleApplication.kt):

| Entity | Strategy | Rationale | Mock-server demo |
|--------|----------|-----------|------------------|
| **notes** | `alwaysRemote()` | Server-owned body; device accepts remote on pull | Local body edit + server simulate-edit → server body wins |
| **tags** | `lastWriteWins()` | Simple label rows; timestamp picks winner | Concurrent local + server label edit |
| **tasks** | `gitLike { }` | Title and `completed` merge independently; same-field clash or remote delete → defer | Server title edit + local checkbox → auto-merge; server + local title edit → conflict sheet |

```kotlin
import dev.syncforge.sample.conflicts.sampleEntityConflicts

conflicts {
    sampleEntityConflicts()
}
```

**Tasks `gitLike` policy** (abbreviated — full `threeWayMerge` in `SampleConflictPolicies.kt`):

```kotlin
entity("tasks") {
    gitLike<TaskEntity> {
        threeWayMerge { base, local, remote ->
            val titleConflict =
                local.title != base.title && remote.title != base.title && local.title != remote.title
            val completedConflict =
                local.completed != base.completed &&
                    remote.completed != base.completed &&
                    local.completed != remote.completed
            if (titleConflict || completedConflict) {
                ThreeWayMergeResult.Unmergeable
            } else {
                ThreeWayMergeResult.Merged(
                    local.copy(
                        title = when {
                            local.title != base.title -> local.title
                            remote.title != base.title -> remote.title
                            else -> local.title
                        },
                        completed = when {
                            local.completed != base.completed -> local.completed
                            remote.completed != base.completed -> remote.completed
                            else -> local.completed
                        },
                        updatedAtMillis = maxOf(local.updatedAtMillis, remote.updatedAtMillis),
                        syncState = SyncState.SYNCED,
                    ),
                )
            }
        }
        onUnmergeable { deferToUser() }
        onRemoteDelete { deferToUser() }
    }
}
```

More recipes: [RECIPES.md → `:sample` conflict matrix](RECIPES.md#sample-conflict-matrix-12).

---

## `gitLike { }` — three-way merge flow

Git-like merge compares **merge base** (last synced snapshot), **local**, and **remote**:

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
              → SYNCED (+ push)        → onUnmergeable { deferToUser() }
                                       → CONFLICT + resolution sheet
```

Merge bases are recorded per `(entityType, entityId)` on push ack and non-conflict pull apply
([`MergeBaseRecorder`](../syncforge/src/commonMain/kotlin/dev/syncforge/conflict/MergeBaseRecorder.kt)).

**`:sample` task demos**

| User action | Result |
|-------------|--------|
| **Server edit** + local **checkbox** toggle → Sync | Auto-merge: server title + local `completed`; no sheet |
| **Server edit** + local **title** edit → Sync | `deferToUser()` → **Conflict — tap Resolve** |
| **Server delete** + local edit → Sync | `deferToUser()` → delete vs update sheet |

---

## Full sync cycle, `localVersion`, and outbox reconcile

### Sync order

`SyncManager.sync()` runs **push → pull → trailing push** when pull leaves new outbox work
(e.g. after a git-like auto-merge re-enqueues a merged `UPDATE`).

### Version alignment

| Column | Role |
|--------|------|
| `localVersion` | Client counter; must equal `serverVersion` when row is `SYNCED` |
| `serverVersion` | Server counter on each pull delta |

On conflict resolution, SyncForge aligns `localVersion` to the pulled `serverVersion`
(`AcceptRemote`) or `serverVersion + 1` when a reconciled merged row must be pushed
([`ConflictPullApplier`](../syncforge/src/commonMain/kotlin/dev/syncforge/conflict/ConflictPullApplier.kt)).

### Outbox reconciliation (1.2-11)

After pull-time resolution, [`OutboxReconciler`](../syncforge/src/commonMain/kotlin/dev/syncforge/sync/OutboxReconciler.kt) keeps the outbox consistent:

| Resolution | Outbox effect |
|------------|---------------|
| `AcceptRemote` / `DeleteLocal` | Remove stale entries for `(entityType, entityId)` |
| `Merged` | Replace stale entries with one `UPDATE` for the merged entity |
| `KeepLocal` | Retain existing entry; enqueue if none remain |

User resolution via `resolveConflict()` uses the same applier and passes `remoteServerVersion`
from the open `ConflictRecord`.

`:mock-server` rejects stale `UPDATE` pushes after `POST /dev/simulate-edit` (OCC on
`localVersion`) so server edits survive until pull. Dev routes bump `updatedAtMillis` using
payload timestamps to stay deterministic across emulator vs host clock skew.

---

## Strategy details

### Last-write-wins

```kotlin
entity("tags") { lastWriteWins() }
```

Compares `updatedAtMillis`. Newer timestamp wins. If local is strictly newer, **KeepLocal**
applies — the row may stay `PENDING` until a successful push. Server tombstones always delete
locally.

**Best for:** simple rows, low collision risk (sample **tags**).

### Always local / always remote

```kotlin
entity("drafts") { alwaysLocal() }
entity("notes") { alwaysRemote() }   // sample notes
```

`alwaysLocal()` keeps the local row and re-queues for push.  
`alwaysRemote()` overwrites the local row with the server copy and clears stale outbox entries.

**Best for:** device-owned drafts vs server-owned content (sample **notes**).

### Custom `merge { }` (two-way)

```kotlin
entity("tasks") {
    merge<TaskEntity> { local, remote ->
        local.copy(
            title = preferNewer(local.title, remote.title),
            completed = preferLocal(local.completed, remote.completed),
            updatedAtMillis = maxOf(local.updatedAtMillis, remote.updatedAtMillis),
            syncState = SyncState.SYNCED,
        )
    }
}
```

See [Recipes → merge { }](RECIPES.md#custom-merge-with-merge--).

**Best for:** independent fields without a stored merge base.

### `deferToUser()`

```kotlin
onUnmergeable { deferToUser() }   // inside gitLike, or standalone per entity
```

On defer:

1. Local row → `SyncState.CONFLICT`
2. `ConflictRecord` saved (local + remote JSON snapshots)
3. `SyncManager.conflicts` emits `ConflictSummary`
4. UI calls `resolveConflict()` when the user decides

**Best for:** same-field clashes, delete vs update, high-value data.

### `crdt { }` *(experimental)*

Per-field CRDT registers (`LwwRegister`, `OrSet`, `GCounter`) for additive merges. See
[`CrdtMergeStrategy`](../syncforge/src/commonMain/kotlin/dev/syncforge/conflict/CrdtMergeStrategy.kt)
and KSP `@Lww` / `@OrSet` / `@GCounter` annotations.

---

## Conflict lifecycle (`deferToUser`)

```
Pull detects conflict
        │
        ▼
ConflictRecord saved (localJson + remoteJson snapshots)
        │
        ▼
SyncManager.conflicts emits ConflictSummary
        │
        ▼
UI shows resolution sheet
        │
        ├── KeepLocal     → resolveConflict(KeepLocal)     → re-push local
        ├── AcceptRemote  → resolveConflict(AcceptRemote)  → overwrite / delete local
        └── Custom(merged)→ resolveConflict(Custom)        → merged entity + outbox UPDATE
        │
        ▼
ConflictRecord marked resolved; outbox reconciled; syncState → SYNCED or PENDING
```

### API reference

```kotlin
val conflicts: StateFlow<List<ConflictSummary>>
val conflictHistory: StateFlow<List<ConflictSummary>>

suspend fun findOpenConflict(entityType: String, entityId: String): ConflictRecord?

suspend fun resolveConflict(
    entityType: String,
    entityId: String,
    choice: ConflictChoice,  // KeepLocal | AcceptRemote | Custom(merged)
)
```

---

## Multi-entity isolation

SyncForge resolves conflicts **per `(entityType, entityId)`**. One entity in `CONFLICT` or
`PENDING` does not block sync for other types on the same `SyncManager`.

The sample proves this with notes (`alwaysRemote`), tags (`lastWriteWins`), and tasks
(`gitLike`) on shared outbox + cursor. A task stuck in **Conflict — tap Resolve** does not
prevent a new note or tag from reaching **Synced** in the same sync session.

The `:sample` app links notes to tags by `tagId` to demonstrate related rows syncing
independently — not built-in tree or cascade semantics.

---

## Compose UI

| Component | Purpose |
|-----------|---------|
| `SyncConflictChip` | Toolbar badge showing open conflict count |
| `SyncConflictResolutionSheet` | Side-by-side local vs remote with action buttons ([COMPOSE_UI.md](COMPOSE_UI.md) — CMP on Android/JVM/Apple) |
| `SyncDebugLauncher` | Debug panel (outbox, conflicts, event log) |

Full wiring: [Recipes → deferToUser](RECIPES.md#handle-defertouser-conflicts-in-compose).  
Canonical implementation: `TasksScreen` + `TasksViewModel`.

---

## Choosing a strategy per entity

| Entity type | Recommended strategy | Why |
|-------------|---------------------|-----|
| Server-owned content (sample **notes**) | `alwaysRemote()` | Device accepts server copy on pull |
| Simple labels / metadata (sample **tags**) | `lastWriteWins()` | Timestamp picks winner; no UI |
| Independent fields (sample **tasks**) | `gitLike { }` or `merge { }` | Title vs checkbox-style fields compose |
| Same-field collaborative edit | `deferToUser()` or `gitLike` + `onUnmergeable { deferToUser() }` | Human picks winner |
| App config / feature flags | `alwaysRemote()` | Server is source of truth |
| Local drafts | `alwaysLocal()` | Device owns until publish |
| Additive sets / counters | `crdt { }` | OR-set / G-counter merge |
| Legal / audit-sensitive | `deferToUser()` | Explicit user consent |

See [Best Practices → Choosing a strategy](BEST_PRACTICES.md#choosing-a-conflict-strategy).

---

## Delete vs update conflicts

Deletes are **tombstones** on the wire (`isDeleted: true` in pull deltas; `ChangeType.DELETE`
on push). See [REST API → tombstones](REST_API.md#push-semantics).

| Local state | Remote delta | `lastWriteWins()` | `deferToUser()` / `gitLike` + `onRemoteDelete` |
|-------------|--------------|-------------------|-----------------------------------------------|
| `PENDING` update | Tombstone | Newer `updatedAtMillis` usually wins | **Conflict** — user picks keep local or accept delete |
| `SYNCED` | Tombstone | Row deleted locally | Row deleted locally |

**Sample delete-conflict flow:** sync task → edit locally → **Server delete** → **Sync** →
resolve in sheet. When remote is a tombstone, `ConflictRecord.remoteJson` is `null` — show
**“Deleted on server”**; `AcceptRemote` removes the local row.

Tombstone-aware merge recipes: [RECIPES.md](RECIPES.md) and roadmap **1.2-04**.

---

## Hierarchical data (trees, parent/child)

SyncForge does **not** cascade deletes or merges across related rows. Conflicts are per entity.

**App responsibilities:**

1. Model relationships explicitly (e.g. `note.tagId` → `tags` row).
2. Define orphan policy (reject child push, soft-delete parent, cleanup job).
3. Mix strategies per type (`alwaysRemote()` parents, `deferToUser()` children, etc.).

**Recipes and limitations:** [HIERARCHICAL_SYNC.md](HIERARCHICAL_SYNC.md) (1.5-04) —
optional FK (`:sample` notes/tags), soft-delete parents, server `VALIDATION`, client orphan cleanup.

See also [Best Practices → Hierarchical data](BEST_PRACTICES.md#hierarchical-data-trees-and-relationships).

---

## E2E verification (`./gradlew androidE2e`)

Instrumented tests run on an emulator against `:mock-server` on `10.0.2.2:8080`.

### `ConflictStrategyE2ETest` — per-strategy + multi-entity (1.2-05)

| Test | Proves |
|------|--------|
| `tags_lww_remoteNewerWinsOnConcurrentEdit` | Tag LWW — remote newer wins |
| `tags_lww_localNewerWinsOnConcurrentEdit` | Tag LWW — local newer stays pending |
| `tasks_gitLike_unmergeableTitleClash_defersToUser` | Title clash → conflict sheet |
| `tasks_gitLike_unmergeableTitleClash_resolveAcceptRemote` | Accept remote resolves |
| `tasks_gitLike_unmergeableTitleClash_resolveKeepLocal` | Keep local resolves |
| `notes_alwaysRemote_acceptsServerOnConcurrentBodyEdit` | Server body wins on concurrent edit |
| `notes_alwaysRemote_localNewerStillAcceptsServer` | Local newer timestamp still accepts server |
| `multiEntity_taskAutoMerge_noteStillSyncs` | Task auto-merge does not block note sync |
| `multiEntity_taskDefer_noteStillSyncs` | Task defer does not block note sync |
| `multiEntity_taskDefer_tagStillSyncs` | Task defer does not block tag sync |
| `multiEntity_taskDefer_noteAlwaysRemoteStillSyncs` | Note `alwaysRemote` under open task conflict |

### Related suites

| Suite | Coverage |
|-------|----------|
| `TasksE2ETest` | Task add/sync, gitLike auto-merge, local delete |
| `MultiEntityE2ETest` | Task + note on one sync; conflict isolation |
| `SampleScenariosE2ETest` | Full tab matrix, delete-conflict, pull restore, note↔tag |

---

## Debugging conflicts

1. Enable `SyncDebugLauncher` (debug builds).
2. Open **Conflicts** tab — inspect `localJson` / `remoteJson`.
3. Check **History** / event log for auto-resolved vs deferred outcomes.
4. Use mock-server dev routes to reproduce.

```bash
./gradlew androidE2e
# Or mock-server only:
./gradlew :mock-server:run
./gradlew :sample:installDebug
```

**Manual repro (tasks):** Add task → Sync → Server edit → edit a different field locally → Sync.  
**Delete conflict:** Add task → Sync → edit locally → Server delete → Sync → Resolve.

---

## FAQ

**Does conflict detection run on push?**  
No. Policies run on **pull**. Push `CONFLICT` rejections are OCC failures; pull applies the
strategy against the server delta.

**What if I never configure `conflicts { }`?**  
All entity types use `lastWriteWins()`. No user prompts, no conflict table entries.

**Do resolved conflicts survive app restart?**  
Yes. `deferToUser()` records persist in SQLDelight until resolved.

**Can I mix strategies across entity types?**  
Yes. Each `entity("type") { }` block is independent — see the `:sample` matrix.

**What happens to the outbox after auto-merge?**  
`OutboxReconciler` replaces stale entries with a merged `UPDATE`. Full sync runs a **trailing
push** so the merged row can reach `SYNCED` in one user-visible sync.

**What happens to the outbox after `AcceptRemote`?**  
Stale outbox entries for that entity are removed. The local row is overwritten with the server
copy and `localVersion` is aligned to `serverVersion`.

**Why does mock-server reject my push after Server edit?**  
By design — simulate-edit bumps `serverVersion` so stale `UPDATE` rows fail OCC until pull
merges or the user resolves.

---

## See also

| Doc | Topic |
|-----|-------|
| [RECIPES.md](RECIPES.md) | Copy-paste `conflicts { }` wiring and Compose sheets |
| [BEST_PRACTICES.md](BEST_PRACTICES.md) | Version bumps, strategy choice, hierarchical data |
| [ROADMAP_1_0_TO_2_0.md](ROADMAP_1_0_TO_2_0.md) | 1.2 job IDs and acceptance criteria |
| [REST_API.md](REST_API.md) | Push/pull contract, tombstones, `serverVersion` |