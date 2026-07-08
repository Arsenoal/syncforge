# Getting Started

Get a working offline-first Android app with SyncForge in **under 10 minutes**.

By the end you will have:

- A sync-aware entity backed by **Room** (default path) or your own store (see [BYO store](#path-b--bring-your-own-store-syncforgestore))
- KSP-generated sync handlers (no hand-written boilerplate)
- Optimistic local writes with automatic outbox queuing
- A manual **Sync** button and status label
- Background sync via WorkManager

> **Prerequisites:** Android Studio, Kotlin 2.1+, minSdk 24, JVM 17. Basic familiarity with Compose helps; Room knowledge is useful for the default path but **not required** if you bring your own store.

---

## How SyncForge fits in your app

```
┌─────────────┐     enqueueChange()     ┌──────────────┐
│  Your UI    │ ──────────────────────► │ SyncManager  │
│  ViewModel  │                         │              │
└─────────────┘                         │  ┌────────┐  │
       ▲                                │  │ Outbox │  │──► SQLDelight (syncforge.db)
       │ observe Room                   │  └────────┘  │
       │                                │  ┌────────┐  │
┌─────────────┐                         │  │ Engine │  │──► KtorSyncTransport
│  Room DAO   │ ◄── optimistic write ──│  └────────┘  │         │
│  (your DB)  │                         └──────────────┘         ▼
└─────────────┘                                              Your API
```

**You own:** entity persistence (Room, Realm, SQLDelight, in-memory, custom), UI, backend.  
**SyncForge owns:** internal outbox + conflict DB, push/pull cycles, retry, cursor, conflict policy.

### Optional — built-in auth

If your backend has login/register endpoints, add `auth { }` and use the same `SyncManager` for
login and sync (no separate auth SDK). See [AUTH_API.md → Android auth flow](AUTH_API.md#android-auth-flow)
for the full diagram and Compose examples.

---

## Step 0 — Add dependencies

### Android (published — recommended)

One **plugin** + one **library** line. The plugin applies KSP, Kotlin serialization, and wires
`syncforge-ksp` automatically. **Room KSP compiler** is added only when your sources use Room
(`@SyncForgeDao` or `androidx.room`) — skip it for BYO-store apps (see [Path B](#path-b--bring-your-own-store-syncforgestore)).

**Version catalog (recommended)** — pins every SyncForge library and the Gradle plugin without `platform(...)`:

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
            from("studio.syncforge:syncforge-catalog:1.2.0")
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

Optional modules use the same pin, e.g. `implementation(syncforge.transport.supabase)`.

**BOM (alternative)** — same version alignment via `platform(...)`:

```kotlin
dependencies {
    implementation(platform("studio.syncforge:syncforge-bom:1.2.0"))
    implementation("studio.syncforge:syncforge")
}
```

| Coordinate | You declare it? | Notes |
|------------|-----------------|-------|
| `studio.syncforge:syncforge` | **Yes** | Main KMP library (`syncforge.core` in catalog) |
| `studio.syncforge:syncforge-catalog` | Optional | Published version catalog — pins all library + plugin versions |
| `studio.syncforge:syncforge-bom` | Optional | Maven BOM — same pins as catalog |
| `studio.syncforge:syncforge-annotations` | No | Transitive via `syncforge` |
| `studio.syncforge:syncforge-persistence` | No | Transitive runtime |
| `studio.syncforge:syncforge-ksp` | No | Added by `studio.syncforge.android` plugin |
| `studio.syncforge:syncforge-network-ktor` | No | Added by `studio.syncforge.android` plugin (default REST transport) |
| `studio.syncforge:syncforge-store-room` | Optional | Room DAO → `EntityStore` adapter for `@SyncForgeStore` |
| `studio.syncforge:syncforge-store-inmemory` | Optional | In-memory `EntityStore` for unit tests / prototyping |
| `studio.syncforge:syncforge-integration-koin` | Optional | Koin `syncForgeModule` + WorkManager helper |
| `studio.syncforge:syncforge-integration-hilt` | Optional | Hilt `SyncForgeHilt` factory helpers |
| Room / WorkManager / serialization | No | Transitive on Android via `syncforge` when using Room |

Your app adds **your** persistence layer. For Room, `room-runtime` is usually already on the
classpath via SyncForge's Android transitive deps. For BYO store, add only what your adapter needs.

### iOS / shared KMP module

```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("studio.syncforge:syncforge:1.1.0")
            implementation("studio.syncforge:syncforge-network-ktor:1.1.0")  // default Ktor REST transport
        }
    }
}
```

Apply KSP on the module that owns `@SyncForgeEntity` and `@SyncForgeDao` or `@SyncForgeStore`
(typically `androidTarget` in the same project). Link the iOS framework in Xcode — see
[IOS_SETUP.md](IOS_SETUP.md).

### JVM / Desktop

```kotlin
dependencies {
    implementation("studio.syncforge:syncforge:1.1.0")
    implementation("studio.syncforge:syncforge-network-ktor:1.1.0")  // default Ktor REST transport
}
```

### Local development (this repo)

The `:sample` app uses project dependencies:

```kotlin
pluginManagement { includeBuild("syncforge-gradle-plugin") }
// app/build.gradle.kts
plugins { id("studio.syncforge.android") }
dependencies { implementation(project(":syncforge")) }
```

Validate the published setup locally:

```bash
./gradlew verifyConsumerSmoke
```

See [consumer-smoke/README.md](../consumer-smoke/README.md).

---

## Step 1 — Define a sync-aware entity (~3 min)

**Path A (default):** Room entity + DAO. **Path B:** any `EntityStore` — [BYO store](#path-b--bring-your-own-store-syncforgestore).

Your entity must implement `SyncedEntity` and be annotated for KSP. On the Room path, add
`@Entity` / `@Dao` as usual.

```kotlin
@SyncForgeEntity(entityType = "tasks")
@Entity(tableName = "tasks")
@Serializable
data class TaskEntity(
    @PrimaryKey override val id: String,
    val title: String,
    val completed: Boolean = false,
    override val localVersion: Long = 0,
    override val updatedAtMillis: Long = System.currentTimeMillis(),
    override val syncState: SyncState = SyncState.SYNCED,
) : SyncedEntity {
    companion object {
        const val ENTITY_TYPE = "tasks"
    }
}
```

| Field | Purpose |
|-------|---------|
| `id` | String primary key (UUID recommended) |
| `localVersion` | Increments on every local edit |
| `updatedAtMillis` | Timestamp for conflict resolution |
| `syncState` | `SYNCED`, `PENDING`, `CONFLICT`, or `FAILED` |

Add a DAO with the methods KSP expects:

```kotlin
@SyncForgeDao(entityClass = "com.example.tasks.TaskEntity")
@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: String)
}
```

**Build the project once.** KSP generates:

- `TaskEntitySyncHandler` — bridges SyncForge to your DAO
- `SyncForgeHandlers.registry(taskDao)` — wires all handlers into an `EntityRegistry`

You never hand-write handler boilerplate.

---

## Path B — Bring your own store (`@SyncForgeStore`)

Use this when you **don't** want Room, or when you prefer a single `EntityStore` port for all
persistence (Room, Realm, SQLDelight, DataStore, in-memory, etc.).

SyncForge syncs through **`EntityStore<T>`** — your app implements `findById`, `upsert`, `delete`,
and optionally `transaction { }`. KSP generates an `EntityStoreSyncHandler` from `@SyncForgeStore`.

### Choose an integration path

| Path | You annotate | KSP generates | Optional artifact |
|------|--------------|---------------|-------------------|
| **A — Room DAO** (above) | `@SyncForgeDao` on DAO | Handler talks to DAO directly | — |
| **B — Room via store** | `@SyncForgeStore` on store class | `EntityStoreSyncHandler` | `syncforge-store-room` |
| **C — Custom store** | `@SyncForgeStore` on your `EntityStore` | `EntityStoreSyncHandler` | — |
| **D — In-memory (tests)** | `@SyncForgeStore` on store class | `EntityStoreSyncHandler` | `syncforge-store-inmemory` |

Use **one adapter per entity** — not both `@SyncForgeDao` and `@SyncForgeStore` on the same type.

### 1. Entity (no Room required)

```kotlin
@SyncForgeEntity(entityType = "tasks")
@Serializable
data class TaskEntity(
    override val id: String,
    val title: String,
    override val localVersion: Long = 0,
    override val updatedAtMillis: Long = System.currentTimeMillis(),
    override val syncState: SyncState = SyncState.SYNCED,
) : SyncedEntity
```

Add `@Entity` / Room only if you persist with Room.

### 2. Store + KSP

**Custom `EntityStore`:**

```kotlin
@SyncForgeStore(entityClass = "com.example.tasks.TaskEntity")
class TaskEntityStore : EntityStore<TaskEntity> {
    override suspend fun findById(id: String): TaskEntity? = /* your backend */
    override suspend fun upsert(entity: TaskEntity) { /* insert or replace */ }
    override suspend fun delete(id: String) { /* remove by id */ }
}
```

**Room via adapter** (`syncforge-store-room`):

```kotlin
implementation("studio.syncforge:syncforge-store-room:1.1.0")

@Dao
interface TaskDao : SyncForgeRoomDao<TaskEntity> { /* findById, insert, update, deleteById */ }

@SyncForgeStore(entityClass = "com.example.tasks.TaskEntity")
class TaskEntityStore(dao: TaskDao, db: AppDatabase) : RoomEntityStore<TaskEntity>(dao, db)
```

**In-memory (unit tests / prototyping)** (`syncforge-store-inmemory`):

```kotlin
testImplementation("studio.syncforge:syncforge-store-inmemory:1.1.0")

@SyncForgeStore(entityClass = "com.example.tasks.TaskEntity")
class TaskEntityStore : InMemoryEntityStore<TaskEntity>()
```

Build once. KSP generates `TaskEntitySyncHandler` and `SyncForgeHandlers.registry(taskEntityStore)`.

### 3. Wire `SyncManager`

```kotlin
val taskStore = TaskEntityStore(/* dao/db or in-memory */)

syncManager = SyncForge.android(this) {
    baseUrl("https://api.example.com")
    registry(SyncForgeHandlers.registry(taskStore))
}
```

Repository code is the same as Path A: call `syncManager.enqueueChange()` for synced mutations.
Read/observe through your store (or a DAO you keep for queries only).

### 4. Gradle — skip Room KSP when unused

The `studio.syncforge.android` plugin **auto-detects** Room usage (`@SyncForgeDao` or
`androidx.room` in sources). When you only use `@SyncForgeStore` without Room, Room compiler is
**not** added to KSP.

Force either way in `app/build.gradle.kts`:

```kotlin
syncForge {
    roomCodegen = false   // BYO store — no Room KSP
    // roomCodegen = true  // force Room compiler even if detection misses a module
}
```

You still need `id("studio.syncforge.android")` for SyncForge KSP and Kotlin serialization.

### Realm / other ORMs

Implement `EntityStore<T>` against your ORM, annotate with `@SyncForgeStore`, and register the
generated handler. No Realm dependency in `:syncforge` core. See
[Recipes → BYO entity store](RECIPES.md#byo-entity-store-syncforgestore).

---

## Step 2 — Wire SyncForge in Application (~2 min)

`SyncForge.android { }` applies Android defaults: SQLDelight outbox + conflicts (`syncforge.db`),
automatic Room → SQLDelight migration on upgrade, persisted pull cursor, connectivity monitoring,
Ktor transport, exponential retry, and WorkManager scheduling. See [ANDROID_SETUP.md](ANDROID_SETUP.md)
for the Room → SQLDelight migration path (legacy `useRoomPersistence()` was removed in 1.0.0).

```kotlin
class MyApplication : Application(), Configuration.Provider {

    lateinit var syncManager: SyncManager
        private set

    override val workManagerConfiguration: Configuration
        get() = SyncForgeAndroid.workManagerConfiguration { syncManager }

    override fun onCreate() {
        super.onCreate()

        val taskDao = TaskDatabase.create(this).taskDao()

        syncManager = SyncForge.android(this) {
            baseUrl("https://api.example.com")   // or http://10.0.2.2:8080 for emulator + mock server
            registry(SyncForgeHandlers.registry(taskDao))
            schedulePeriodicSyncOnStart()
        }
    }
}
```

Required imports:

```kotlin
import dev.syncforge.SyncForge
import dev.syncforge.SyncForgeAndroid
import dev.syncforge.android
import com.example.tasks.SyncForgeHandlers   // KSP-generated in your package
```

**That's the entire SyncForge setup.** No duplicated `entityTypes`, no manual outbox factory,
no transport wiring unless you want to customize.

### HTTP client — default vs injectable

By default, `SyncForge.android { }` uses `KtorSyncTransport` from `syncforge-network-ktor`
(OkHttp on Android). SyncForge calls your backend at **`POST /sync/push`** and
**`GET /sync/pull`** only — see [REST API](REST_API.md).

| Approach | When to use |
|----------|-------------|
| **Omit `httpClient()`** | Quick start, sample apps, no existing Ktor stack |
| **`httpClient(appHttpClient)`** | You already have a Ktor `HttpClient` (logging, shared engine, app interceptors) |

```kotlin
// Reuse an app-owned client (logging, shared OkHttp engine, etc.)
SyncForge.android(this) {
    baseUrl("https://api.example.com")
    httpClient(appHttpClient)
    registry(SyncForgeHandlers.registry(taskDao))
}
```

**Important:** SyncForge uses your client as-is. Bearer tokens and JSON negotiation must already
be configured on `appHttpClient` (or use `buildSyncForgeHttpClient` from `syncforge-network-ktor`).
`authToken { }` / `auth { }` still wire 401 refresh at the transport layer, but injected clients
do not get automatic `Authorization` headers unless you add them. See
[Recipes → Inject app-owned Ktor HttpClient](RECIPES.md#inject-app-owned-ktor-httpclient).

**iOS:** add `syncforge-network-ktor`, call `ensureSyncForgeNetworkKtorLoaded()` once at startup
before `SyncForge.ios { }`, or pass `httpClient()` / `transport(KtorSyncTransport(...))` explicitly.
Details: [IOS_SETUP.md → Transport](IOS_SETUP.md#transport).

**Advanced:** implement `SyncTransport` yourself (GraphQL, Firebase, etc.) and pass
`transport(customTransport)` — no Ktor required.

---

## Step 3 — Queue changes from a repository (~2 min)

Never write to Room directly for synced data — go through `syncManager.enqueueChange()`.
SyncForge applies the write optimistically, snapshots for rollback, and enqueues the outbox entry.

```kotlin
class TaskRepository(
    private val taskDao: TaskDao,
    private val syncManager: SyncManager,
) {
    fun observeTasks(): Flow<List<TaskEntity>> = taskDao.observeAll()

    suspend fun addTask(title: String) {
        val now = System.currentTimeMillis()
        val task = TaskEntity(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            localVersion = 1,
            updatedAtMillis = now,
            syncState = SyncState.PENDING,
        )
        syncManager.enqueueChange(Change.create(TaskEntity.ENTITY_TYPE, task))
    }

    suspend fun toggleCompleted(task: TaskEntity) {
        val updated = task.copy(
            completed = !task.completed,
            localVersion = task.localVersion + 1,
            updatedAtMillis = System.currentTimeMillis(),
            syncState = SyncState.PENDING,
        )
        syncManager.enqueueChange(Change.update(TaskEntity.ENTITY_TYPE, updated))
    }

    suspend fun sync() = syncManager.sync()
}
```

**Key rule:** bump `localVersion` and `updatedAtMillis` on every edit. Conflict strategies
use these fields.

---

## Step 4 — Show status and sync in Compose (~2 min)

### Option A — Composable helper (simplest)

```kotlin
@Composable
fun SyncBanner(syncManager: SyncManager) {
    val uiModel = syncManager.collectSyncStatusUiModel()
    Text(
        text = uiModel.label,
        color = if (uiModel.isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
```

### Option B — ViewModel with `StateFlow` (recommended for screens)

```kotlin
val syncStatus: StateFlow<SyncStatusUiModel> =
    syncManager.status
        .map { it.toUiModel() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), syncManager.status.value.toUiModel())
```

Wire a sync button:

```kotlin
TextButton(onClick = { viewModel.sync() }) {
    Text(if (syncStatus.isSyncing) "Syncing…" else "Sync")
}
```

`SyncStatusUiModel.label` covers the common cases: "Up to date", "3 changes pending",
"Offline · 2 changes queued", "1 conflict needs resolution".

---

## Step 5 — Run locally with the mock server (~1 min)

If you don't have a backend yet, use the included reference server:

| Goal | Module | Command |
|------|--------|---------|
| Minimal production starter (Ktor) | `:backend-starter` | `./gradlew :backend-starter:run` |
| Minimal production starter (Spring) | `:backend-starter-spring` | `./gradlew :backend-starter-spring:bootRun` |
| Conflict demo (`/dev/simulate-edit`) | `:mock-server` | `./gradlew :mock-server:run` |

```bash
# Terminal 1 — pick one
./gradlew :backend-starter:run
# or: ./gradlew :mock-server:run

# Terminal 2 — point baseUrl at the emulator host alias
./gradlew :sample:installDebug
```

Set `baseUrl` to `http://10.0.2.2:8080` in debug builds (emulator → host machine).

### Verify it works

| Action | Expected result |
|--------|-----------------|
| Add a task | Appears instantly in the list (`syncState = PENDING`) |
| Tap **Sync** | Status shows "Syncing…" then "Up to date"; `syncState = SYNCED` |
| Turn off network, add a task | Status shows "Offline · 1 change queued" |
| Turn network back on | Auto-push retries; status returns to synced |
| Kill and reopen the app | Pending changes still in outbox (SQLDelight-persisted) |

---

## Try the conflict demo (optional)

The sample app demonstrates `deferToUser()` conflicts:

1. Add a task and tap **Sync** (pushes to server)
2. Tap **Server edit** on a synced task (mock server simulates a remote change)
3. Edit the task locally, then tap **Sync**
4. A conflict chip appears — resolve via the bottom sheet

See [Conflict Resolution](CONFLICT_RESOLUTION.md) and [Recipes → deferToUser](RECIPES.md#handle-defertouser-conflicts-in-compose) for your own UI.

---

## What you get for free

When you use `SyncForge.android { }`, these are configured automatically:

| Feature | Default |
|---------|---------|
| Outbox storage | SQLDelight (`syncforge.db`) |
| Pull cursor | DataStore Preferences (Android); migrates legacy SharedPreferences on upgrade |
| Transport | `KtorSyncTransport` via `syncforge-network-ktor` (override with `httpClient()` or `transport()`) |
| Push retry | Exponential backoff, max 5 attempts |
| Network reconnect | Auto-push when connectivity returns |
| Conflict default | Last-write-wins (override per entity) |
| Background sync | WorkManager periodic job (15 min minimum) |

---

## Common first-time issues

| Symptom | Fix |
|---------|-----|
| `SyncForgeHandlers` not found | Build once; ensure `id("studio.syncforge.android")` is applied (adds KSP automatically) |
| Sync fails with network error on emulator | Use `http://10.0.2.2:8080`, not `localhost` |
| Entity not syncing | Ensure `entityType` in `@SyncForgeEntity` matches `Change.create("tasks", …)` |
| `findById` missing | `@SyncForgeDao` handlers need `findById` on the DAO; `@SyncForgeStore` needs `EntityStore.findById` |
| Room KSP runs but I use BYO store only | Set `syncForge { roomCodegen = false }` or remove `androidx.room` imports from sources |
| `@SyncForgeStore` / `@SyncForgeDao` clash | One adapter per entity — pick DAO path or store path, not both |
| WorkManager not running | Implement `Configuration.Provider` with `SyncForgeAndroid.workManagerConfiguration` |

---

## Next steps

| Topic | Guide |
|-------|-------|
| BYO entity store (`@SyncForgeStore`) | [Recipes → BYO entity store](RECIPES.md#byo-entity-store-syncforgestore) |
| Koin / Hilt wiring | [Recipes → Dependency injection](RECIPES.md#dependency-injection-koin--hilt) |
| Injectable Ktor `HttpClient` | [Recipes → Inject app-owned HttpClient](RECIPES.md#inject-app-owned-ktor-httpclient) |
| GraphQL transport | [Recipes → GraphQL sync transport](RECIPES.md#graphql-sync-transport-client) |
| BaaS `SyncDeltaStore` | [Custom transport → BYO store](CUSTOM_TRANSPORT.md#byo-syncdeltastore) |
| Custom wire format | [Custom transport guide](CUSTOM_TRANSPORT.md) |
| Custom field merges | [Recipes → merge { }](RECIPES.md#custom-merge-with-merge--) |
| User-driven conflict UI | [Recipes → deferToUser](RECIPES.md#handle-defertouser-conflicts-in-compose) |
| In-app debug console | [Recipes → Debug console](RECIPES.md#use-the-in-app-debug-console) |
| Strategy decision guide | [Conflict Resolution](CONFLICT_RESOLUTION.md) |
| Entity design tips | [Best Practices](BEST_PRACTICES.md) |
| Backend contract | [REST API](REST_API.md) |
| Full API reference | [Module Reference](MODULES.md) |
| Stable vs experimental APIs | [Module Reference → API stability](MODULES.md#api-stability) |
| Upgrade from pre-0.6 Room | [Android Setup → Legacy Room storage](ANDROID_SETUP.md#legacy-room-storage-pre-060) |