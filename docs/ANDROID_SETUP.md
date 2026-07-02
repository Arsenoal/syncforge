# Android setup guide

Configure SyncForge on Android using `SyncForge.android(context) { }`.

---

## Quick start (default — SQLDelight since 0.6.0)

```kotlin
syncManager = SyncForge.android(this) {
    baseUrl("https://api.example.com")
    registry(SyncForgeHandlers.registry(taskDao))
    schedulePeriodicSyncOnStart()
}
```

**Defaults:** SQLDelight outbox + conflicts (`syncforge.db`), automatic Room → SQLDelight migration on upgrade, SharedPreferences cursor, ConnectivityManager, Ktor/OkHttp, WorkManager.

---

## Legacy Room opt-in

Room is **deprecated** since 0.6.0. Only use if you cannot migrate yet:

```kotlin
SyncForge.android(this) {
    useRoomPersistence()   // ← legacy Room backend
    // ...
}
```

### Explicit SQLDelight (optional since 0.6.0)

`useSqlDelightPersistence()` is a no-op — SQLDelight is already the default. You may still pass a custom database name:

```kotlin
SyncForge.android(this) {
    baseUrl("https://api.example.com")
    registry(SyncForgeHandlers.registry(taskDao))
    useSqlDelightPersistence(databaseName = "my_app_syncforge.db")
}
```

### Explicit factory

```kotlin
import dev.syncforge.persistence.SyncForgePersistenceFactory

SyncForge.android(this) {
    baseUrl("https://api.example.com")
    registry(SyncForgeHandlers.registry(taskDao))
    persistence(SyncForgePersistenceFactory.create(this))
}
```

`createSyncForgePersistence(context)` is also available as a lower-level alias.

### Custom database name

```kotlin
useSqlDelightPersistence(databaseName = "my_app_syncforge.db")
```

---

## Room vs SQLDelight

| | Room (legacy opt-in) | SQLDelight (default since 0.6.0) |
|--|----------------------|----------------------------------|
| Database file | `syncforge_outbox.db` | `syncforge.db` |
| Module | `:syncforge` androidMain (Room) | `:syncforge-persistence` + `:syncforge` `syncPersistenceMain` |
| KMP shared with iOS | No | Yes |
| API | `OutboxRepository` / `ConflictStore` | Same interfaces |
| Migration | — | Automatic Room → SQLDelight on upgrade |

Both implementations satisfy the same contracts — your entity handlers and `SyncManager` API are unchanged.

---

## Migrating from Room to SQLDelight (0.6.0+)

Since **0.6.0**, `SyncForge.android { }` uses SQLDelight by default and runs [RoomToSqlDelightMigrator](../syncforge/src/androidMain/kotlin/dev/syncforge/persistence/RoomToSqlDelightMigrator.kt) automatically on first launch when `syncforge_outbox.db` exists.

The migrator:

1. Copies all Room outbox rows and conflict records into SQLDelight (preserving row ids).
2. Reseeds SQLite AUTOINCREMENT counters.
3. Deletes `syncforge_outbox.db` after a successful copy.
4. Records completion in `syncforge_migration` SharedPreferences (runs once).

### Manual migration (advanced)

```kotlin
val persistence = SyncForgePersistenceFactory.create(context)
val result = RoomToSqlDelightMigrator.migrateIfNeeded(context, persistence)
```

### Pre-0.6.0 manual cutover (legacy)

If upgrading from a pre-0.6.0 app that still used Room:

1. Ship a release that drains the outbox (`SyncStatus` up to date).
2. Upgrade to 0.6.0+ — automatic migrator handles pending rows if any remain.

### Coexistence during development

Both database files can exist simultaneously — useful for A/B testing persistence backends.

---

## DSL reference

| Method | Description |
|--------|-------------|
| `useSqlDelightPersistence(name?)` | No-op since 0.6.0 (SQLDelight is default); optional custom DB name |
| `useRoomPersistence()` | Deprecated legacy Room backend |
| `persistence(SyncForgePersistence)` | Inject a custom persistence instance |
| `customize { }` | Override `outbox` / `conflictStore` manually |

Manual `builder.outbox` / `builder.conflictStore` via `customize { }` takes precedence over `persistence()`.

---

See [KMP_MIGRATION.md](KMP_MIGRATION.md) for the full multiplatform persistence plan.