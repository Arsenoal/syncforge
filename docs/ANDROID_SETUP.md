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

### Custom database name

SQLDelight is the default since 0.6.0. Override the file name when needed:

```kotlin
SyncForge.android(this) {
    baseUrl("https://api.example.com")
    registry(SyncForgeHandlers.registry(taskDao))
    databaseName("my_app_syncforge.db")
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

---

## Legacy Room storage (pre-0.6.0)

SyncForge's internal outbox/conflict backend is **SQLDelight only** since 0.9.0-rc.5.
The legacy `useRoomPersistence()` opt-in was removed for 1.0.

If your app still has `syncforge_outbox.db` from a pre-0.6.0 release, the automatic
`RoomToSqlDelightMigrator` copies pending rows into `syncforge.db` on first launch — see below.

---

## Migrating from Room to SQLDelight (0.6.0+)

Since **0.6.0**, `SyncForge.android { }` uses SQLDelight by default and runs [RoomToSqlDelightMigrator](../syncforge/src/androidMain/kotlin/dev/syncforge/persistence/RoomToSqlDelightMigrator.kt) automatically on first launch when `syncforge_outbox.db` exists.

The migrator:

1. Copies Room outbox rows and conflict records into SQLDelight in batches (preserving row ids).
2. Reseeds SQLite AUTOINCREMENT counters.
3. Deletes `syncforge_outbox.db` after a successful copy.
4. Records completion in `syncforge_migration` SharedPreferences (runs once).

### Verification

| Test | Command |
|------|---------|
| Migrator unit tests (Robolectric) | `./gradlew :syncforge:testDebugUnitTest` |
| `SyncForge.android` upgrade path | `SyncForgeAndroidMigrationTest` in the same task |
| Sample instrumented upgrade + push | `./gradlew androidE2e` — `RoomMigrationInstrumentedTest` (isolated DB, does not touch app `syncforge.db`) |

On failure, the migrator logs to `SyncForgeMigrator`, leaves the preference unset, and retries on
the next launch (`INSERT OR REPLACE` makes partial progress safe).

### Manual migration (advanced)

```kotlin
val persistence = SyncForgePersistenceFactory.create(context)
val result = RoomToSqlDelightMigrator.migrateIfNeeded(context, persistence)
```

### Pre-0.6.0 manual cutover (legacy)

If upgrading from a pre-0.6.0 app that still used Room:

1. Ship a release that drains the outbox (`SyncStatus` up to date).
2. Upgrade to 0.6.0+ — automatic migrator handles pending rows if any remain.

---

## Built-in auth

Use `auth { }` when your backend exposes register/login/refresh endpoints. SyncForge becomes the
single API for **login + sync** — no separate auth SDK required for the default flow.

```kotlin
SyncForge.android(this) {
    baseUrl("https://api.example.com")
    registry(SyncForgeHandlers.registry(taskDao))
    auth {
        tokenFields(
            accessToken = "access_token",
            refreshToken = "refresh_token",
            expiresInSeconds = "expires_in",
        )
    }
}

// From UI / ViewModel:
syncManager.login(mapOf("email" to email, "password" to password))
syncManager.sync()
```

- Observe `syncManager.authState` (`LoggedOut`, `LoggedIn`, `Refreshing`, `Error`)
- Tokens persist via `TokenStore` (SharedPreferences on Android)
- Bearer token and 401 refresh are wired into `KtorSyncTransport` automatically
- Local reference backend: `./gradlew :backend-starter:run` (emulator: `http://10.0.2.2:8080`)

Full walkthrough with sequence diagram: [AUTH_API.md → Android auth flow](AUTH_API.md#android-auth-flow).

---

## DSL reference

| Method | Description |
|--------|-------------|
| `auth { }` | Built-in register/login/refresh — see [AUTH_API.md](AUTH_API.md) |
| `authToken { }` / `auth(provider)` | Manual bearer or custom `SyncAuthProvider` |
| `databaseName(name)` | SQLDelight database file name (default `syncforge.db`) |
| `persistence(SyncForgePersistence)` | Inject a custom persistence instance |
| `customize { }` | Override `outbox` / `conflictStore` manually |

Manual `builder.outbox` / `builder.conflictStore` via `customize { }` takes precedence over `persistence()`.

---

See [KMP_MIGRATION.md](KMP_MIGRATION.md) for the full multiplatform persistence plan.