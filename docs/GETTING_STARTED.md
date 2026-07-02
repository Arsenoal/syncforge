# Getting Started

Get a working offline-first Android app with SyncForge in **under 10 minutes**.

By the end you will have:

- A Room entity that syncs with a backend
- KSP-generated sync handlers (no hand-written boilerplate)
- Optimistic local writes with automatic outbox queuing
- A manual **Sync** button and status label
- Background sync via WorkManager

> **Prerequisites:** Android Studio, Kotlin 2.1+, minSdk 24, JVM 17. Basic familiarity with Room and Compose helps but is not required.

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

**You own:** Room schema, DAOs, UI, backend.  
**SyncForge owns:** internal outbox + conflict DB, push/pull cycles, retry, cursor, conflict policy.

---

## Step 0 — Add dependencies

### Android (published — recommended)

One **plugin** + one **library** line. The plugin applies KSP, Kotlin serialization, and wires
`syncforge-ksp` + Room compiler — you do **not** declare those manually.

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

// app/build.gradle.kts
plugins {
    alias(libs.plugins.kotlinAndroid)
    id("dev.syncforge.android") version "0.9.0-rc.1"
}

dependencies {
    implementation(platform("dev.syncforge:syncforge-bom:0.9.0-rc.1"))
    implementation("dev.syncforge:syncforge")
}
```

| Coordinate | You declare it? | Notes |
|------------|-----------------|-------|
| `dev.syncforge:syncforge` | **Yes** | Main KMP library |
| `dev.syncforge:syncforge-bom` | Optional | Pins all SyncForge artifact versions |
| `dev.syncforge:syncforge-annotations` | No | Transitive via `syncforge` |
| `dev.syncforge:syncforge-persistence` | No | Transitive runtime |
| `dev.syncforge:syncforge-ksp` | No | Added by `dev.syncforge.android` plugin |
| Room / WorkManager / serialization | No | Transitive on Android via `syncforge` |

Your app still adds **your** Room database (`room-runtime` for `@Database` / `@Dao` only if not
already on the classpath — usually covered by SyncForge's Android transitive deps).

### iOS / shared KMP module

```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("dev.syncforge:syncforge:0.9.0-rc.1")
        }
    }
}
```

Apply KSP on the module that owns `@SyncForgeEntity` / `@SyncForgeDao` (typically `androidTarget`
in the same project). Link the iOS framework in Xcode — see [IOS_SETUP.md](IOS_SETUP.md).

### JVM / Desktop

```kotlin
dependencies {
    implementation("dev.syncforge:syncforge:0.9.0-rc.1")
}
```

### Local development (this repo)

The `:sample` app uses project dependencies:

```kotlin
pluginManagement { includeBuild("syncforge-gradle-plugin") }
// app/build.gradle.kts
plugins { id("dev.syncforge.android") }
dependencies { implementation(project(":syncforge")) }
```

Validate the published setup locally:

```bash
./gradlew verifyConsumerSmoke
```

See [consumer-smoke/README.md](../consumer-smoke/README.md).

---

## Step 1 — Define a sync-aware entity (~3 min)

Your Room entity must implement `SyncedEntity` and be annotated for KSP.

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

## Step 2 — Wire SyncForge in Application (~2 min)

`SyncForge.android { }` applies Android defaults: SQLDelight outbox + conflicts (`syncforge.db`),
automatic Room → SQLDelight migration on upgrade, persisted pull cursor, connectivity monitoring,
Ktor transport, exponential retry, and WorkManager scheduling. See [ANDROID_SETUP.md](ANDROID_SETUP.md)
for legacy Room opt-in.

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
| Minimal production starter | `:backend-starter` | `./gradlew :backend-starter:run` |
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
| Pull cursor | SharedPreferences |
| Transport | `KtorSyncTransport` |
| Push retry | Exponential backoff, max 5 attempts |
| Network reconnect | Auto-push when connectivity returns |
| Conflict default | Last-write-wins (override per entity) |
| Background sync | WorkManager periodic job (15 min minimum) |

---

## Common first-time issues

| Symptom | Fix |
|---------|-----|
| `SyncForgeHandlers` not found | Build once; ensure `id("dev.syncforge.android")` is applied (adds KSP automatically) |
| Sync fails with network error on emulator | Use `http://10.0.2.2:8080`, not `localhost` |
| Entity not syncing | Ensure `entityType` in `@SyncForgeEntity` matches `Change.create("tasks", …)` |
| `findById` missing | KSP-generated handler requires it on `@SyncForgeDao` |
| WorkManager not running | Implement `Configuration.Provider` with `SyncForgeAndroid.workManagerConfiguration` |

---

## Next steps

| Topic | Guide |
|-------|-------|
| Custom field merges | [Recipes → merge { }](RECIPES.md#custom-merge-with-merge--) |
| User-driven conflict UI | [Recipes → deferToUser](RECIPES.md#handle-defertouser-conflicts-in-compose) |
| In-app debug console | [Recipes → Debug console](RECIPES.md#use-the-in-app-debug-console) |
| Strategy decision guide | [Conflict Resolution](CONFLICT_RESOLUTION.md) |
| Entity design tips | [Best Practices](BEST_PRACTICES.md) |
| Backend contract | [REST API](REST_API.md) |
| Full API reference | [Module Reference](MODULES.md) |