# Best Practices

Opinionated guidance for building reliable offline-first apps with SyncForge.

---

## Entity design

### Use String IDs (UUIDs)

```kotlin
id = UUID.randomUUID().toString()
```

String IDs work across platforms, avoid auto-increment collisions, and match the
`SyncedEntity` contract. Generate client-side — never wait for the server to assign IDs
for entities created offline.

### Always bump version metadata on edit

```kotlin
task.copy(
    title = newTitle,
    localVersion = task.localVersion + 1,
    updatedAtMillis = System.currentTimeMillis(),
    syncState = SyncState.PENDING,
)
```

Conflict strategies depend on `localVersion` and `updatedAtMillis`. Forgetting to update
them breaks last-write-wins and merge helpers.

### Keep entities `@Serializable`

KSP-generated handlers serialize payloads to JSON for the outbox and conflict snapshots.
Use kotlinx.serialization with sensible defaults:

```kotlin
@Serializable
data class TaskEntity(/* ... */)
```

### Separate app DB from SyncForge DB

SyncForge stores outbox and conflict rows in its own `SyncForgeDatabase` — not in your
app schema. Your Room database holds synced entities only.

### Avoid syncing derived/computed fields

Don't include UI-only or locally-computed values in the synced payload. Sync only fields
your backend understands.

---

## Repository pattern

### Single entry point for mutations

```kotlin
// Good — goes through SyncForge
syncManager.enqueueChange(Change.update("tasks", updated))

// Bad — bypasses outbox, optimistic write, and rollback
taskDao.update(updated)
```

### Let Room `Flow` drive UI

After `enqueueChange()`, the optimistic write updates Room immediately. Your existing
`dao.observeAll()` Flow refreshes the UI — no manual cache invalidation needed.

### Sync is explicit or scheduled

Call `syncManager.sync()` from a user action, on login, or rely on WorkManager periodic
sync. Don't call `sync()` on every keystroke — batch user edits locally first.

---

## Choosing a conflict strategy

### Decision flowchart

```
Same field edited on two devices?
├── Yes → deferToUser() or merge { } with preferNewer per field
└── No  → Can fields be combined independently?
          ├── Yes → merge { }
          └── No  → Is data low-stakes?
                    ├── Yes → lastWriteWins()
                    └── No  → deferToUser()
```

### Strategy cheat sheet

| Pattern | Strategy |
|---------|----------|
| Server config, feature flags | `alwaysRemote()` |
| Device-only drafts | `alwaysLocal()` |
| Independent fields (name + avatar) | `merge { }` |
| Same field, user must decide | `deferToUser()` |
| Rare collisions, acceptable loss | `lastWriteWins()` (default) |

### Don't defer everything

`deferToUser()` requires UI work and blocks clean sync until resolved. Reserve it for
entity types where data loss is unacceptable. Use `merge { }` or LWW for the rest.

### Per-entity, not per-field

Strategies are configured per `entityType`. If you need different rules for different fields
within one entity, use `merge { }` with field-level helpers.

---

## Performance

### Tune batch and page sizes

```kotlin
SyncForge.android(this) {
    pushBatchSize = 50    // default — raise for bulk imports
    pullPageSize = 100    // default — lower on slow networks
}
```

Large initial syncs benefit from smaller `pullPageSize` to avoid memory spikes.

### Don't observe `debug` flows in production

`syncManager.debug.outboxItems` and `debug.events` are for QA. Guard `SyncDebugLauncher`
with `BuildConfig.DEBUG`.

### Use `SharingStarted.WhileSubscribed(5_000)` in ViewModels

Prevents collecting sync flows when the screen is in the back stack:

```kotlin
syncManager.status
    .map { it.toUiModel() }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
```

### Index Room queries

Your DAO's `observeAll()` runs on every optimistic write. Ensure your ORDER BY columns
are indexed if tables grow large.

---

## Error handling

### Surface `SyncStatus` to users

| Status | User message |
|--------|--------------|
| `Pending` with `permanentlyFailedCount > 0` | "Some changes couldn't sync" |
| `Offline` | "Offline — changes saved locally" |
| `Error` | Show `message`; offer retry if `retryable` |
| `Pending` with `conflictCount > 0` | "Conflicts need your attention" |

### Transient vs permanent failures

- **Network errors** — retried with exponential backoff; optimistic writes kept
- **Validation / auth errors** — marked permanently failed; user must fix data
- Don't manually retry permanently failed entries without fixing the underlying issue

### Auth token lifecycle

Provide a fresh token on each request:

```kotlin
authToken { tokenStore.accessToken }  // not a cached stale value
```

Token refresh on 401 is planned (see [ROADMAP](ROADMAP.md)).

---

## Testing

### Unit test with in-memory stack

```kotlin
val syncManager = SyncForge.builder {
    handler(fakeHandler)
    outbox = InMemoryOutboxRepository()
    transport = fakeTransport
    scope = testScope
}
```

### Test conflict strategies in isolation

Use `ConflictPolicy` + `ConflictPullApplier` with fake handlers. See `commonTest` for examples.

### Use mock server for integration tests

`:mock-server` implements the full REST contract including pagination and `simulate-edit`.

---

## Security

- Enable `SyncDebugLauncher` only in debug builds
- Don't log `ConflictRecord` JSON in production (may contain PII)
- Validate server TLS in production (`KtorSyncTransport` uses HTTPS)
- Implement auth on your backend — mock server ignores it for local dev

---

## Backend alignment

Your server must implement [REST API](REST_API.md):

- Idempotent push (clients retry batches)
- Monotonic `serverVersion` and `updatedAtMillis` on upsert
- Tombstones for deletes (`isDeleted: true`)
- `serverTimestampMillis` as the pull cursor

Mismatch between client `entityType` strings and server storage is the most common
integration bug — use constants:

```kotlin
companion object { const val ENTITY_TYPE = "tasks" }
```