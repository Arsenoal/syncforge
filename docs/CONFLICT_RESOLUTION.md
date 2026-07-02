# Conflict Resolution

SyncForge detects conflicts during **pull** when a remote delta arrives for an entity that
has local pending changes or divergent state. A **conflict strategy** decides what happens next:
auto-merge, pick a winner, or defer to the user.

---

## When does a conflict happen?

A conflict is detected when **all** of these are true:

1. A remote delta arrives for entity `(type, id)`
2. The local row exists and is not cleanly synced (e.g. `PENDING`, or versions diverge)
3. Local and remote payloads are not identical

Typical scenario:

```
Device A: edits task title  →  PENDING in outbox
Device B: edits same task   →  server has newer version
Device A: pulls             →  CONFLICT
```

---

## Strategy overview

Configure strategies in the `conflicts { }` block inside `SyncForge.android { }`:

```kotlin
conflicts {
    // Optional global override (default is last-write-wins)
    default(ConflictStrategies.lastWriteWins())

    entity("tasks") { deferToUser() }
    entity("notes") {
        merge<NoteEntity> { local, remote -> /* ... */ }
    }
    entity("settings") { alwaysRemote() }
}
```

| Strategy | Resolution | User prompt? | Persisted in conflict store? |
|----------|------------|--------------|------------------------------|
| `lastWriteWins()` | Newer `updatedAtMillis` wins | No | No — auto-resolved |
| `alwaysLocal()` | Local row kept, re-pushed | No | No |
| `alwaysRemote()` | Server row replaces local | No | No |
| `merge { }` | Custom field-level combine | No | No |
| `deferToUser()` | Stored until `resolveConflict()` | **Yes** | **Yes** — SQLDelight conflict table |

**Default:** every entity type uses `lastWriteWins()` unless you override it.

---

## Strategy details

### Last-write-wins (default)

```kotlin
entity("tasks") { lastWriteWins() }  // optional — this is the default
```

Compares `updatedAtMillis`. The newer timestamp wins. Server tombstones (`isDeleted = true`)
always delete locally.

**Best for:** simple CRUD, low collision probability, timestamp-trusted edits.

### Always local / always remote

```kotlin
entity("drafts") { alwaysLocal() }    // device is authoritative
entity("config") { alwaysRemote() }   // server is authoritative
```

`alwaysLocal()` marks the row `PENDING` and re-queues for push.  
`alwaysRemote()` overwrites Room silently.

**Best for:** device-owned drafts vs server-owned configuration.

### Custom merge

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

See [Recipes → merge { }](RECIPES.md#custom-merge-with-merge--) for full examples.

**Best for:** independent fields, collaborative content, additive changes.

### Defer to user

```kotlin
entity("tasks") { deferToUser() }
```

On conflict:

1. Local row is marked `SyncState.CONFLICT`
2. A `ConflictRecord` is saved to SyncForge's conflict store (SQLDelight)
3. `SyncManager.conflicts` emits a `ConflictSummary`
4. UI calls `resolveConflict()` when the user decides

**Best for:** high-value data, same-field edits, legal/audit-sensitive records.

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
        ├── KeepLocal  → resolveConflict(KeepLocal)  → re-push local
        │
        └── AcceptRemote → resolveConflict(AcceptRemote) → overwrite Room
        │
        ▼
ConflictRecord marked resolved; syncState → SYNCED or PENDING
```

### API reference

```kotlin
// Open conflicts awaiting user action
val conflicts: StateFlow<List<ConflictSummary>>

// Full audit trail (open + resolved) — for debug panels
val conflictHistory: StateFlow<List<ConflictSummary>>

// Load JSON snapshots for a resolution UI
suspend fun findOpenConflict(entityType: String, entityId: String): ConflictRecord?

// Apply user's choice
suspend fun resolveConflict(
    entityType: String,
    entityId: String,
    choice: ConflictChoice,  // KeepLocal | AcceptRemote | Custom(merged)
)
```

---

## Compose UI

SyncForge ships optional components (Android):

| Component | Purpose |
|-----------|---------|
| `SyncConflictChip` | Toolbar badge showing open conflict count |
| `SyncConflictResolutionSheet` | Side-by-side local vs remote with action buttons |

Full wiring example: [Recipes → deferToUser](RECIPES.md#handle-defertouser-conflicts-in-compose).

The sample app (`TasksScreen` + `TasksViewModel`) is the canonical reference.

---

## Choosing a strategy per entity

| Entity type | Recommended strategy | Why |
|-------------|---------------------|-----|
| User profile | `merge { }` or `deferToUser()` | Different fields edited on different devices |
| Shopping cart | `merge { }` with sum/max | Additive changes compose naturally |
| Todo title | `deferToUser()` | Same field edited offline on two phones |
| App config / feature flags | `alwaysRemote()` | Server is source of truth |
| Local drafts | `alwaysLocal()` | Device owns the draft until explicit publish |
| Low-stakes notes | `lastWriteWins()` | Simplest; acceptable data loss risk |
| Audit logs | `alwaysRemote()` | Append-only server data |

See [Best Practices → Choosing a strategy](BEST_PRACTICES.md#choosing-a-conflict-strategy) for more detail.

---

## Debugging conflicts

1. Enable `SyncDebugLauncher` (debug builds only)
2. Open **Conflicts** tab — inspect `localJson` / `remoteJson` snapshots
3. Check **History** tab for `ConflictDetected` events
4. Use mock server `POST /dev/simulate-edit` to reproduce locally

```bash
./gradlew :mock-server:run
./gradlew :sample:installDebug
# Add task → Sync → Server edit → edit locally → Sync → conflict chip
```

---

## FAQ

**Does conflict detection run on push?**  
No. Conflicts are detected during **pull** when remote deltas arrive. Push rejections with
`CONFLICT` code are handled as server rejections (rollback), not the conflict policy system.

**What if I never configure `conflicts { }`?**  
All entity types use last-write-wins. No user prompts, no conflict table entries.

**Do resolved conflicts survive app restart?**  
Yes. `deferToUser()` records persist in SQLDelight across process death. Open conflicts remain until resolved.

**Can I mix strategies across entity types?**  
Yes. Each `entity("type") { }` block is independent.

**What happens to the outbox during a deferred conflict?**  
The local outbox entry may still exist. Resolution via `KeepLocal` re-queues the local version;
`AcceptRemote` may clear the conflicting state and accept the server copy.