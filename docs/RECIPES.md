# Recipes

Practical copy-paste examples for common SyncForge tasks. Assumes you have completed
[Getting Started](GETTING_STARTED.md).

---

## Custom merge with `merge { }`

Use when two users edit **different fields** and you want to combine both changes automatically
instead of picking a winner.

### When to use

- Collaborative documents (title from Alice, body from Bob)
- Counters or tallies where you can sum or max
- Settings where each field has independent ownership

### Setup

```kotlin
SyncForge.android(this) {
    baseUrl(BuildConfig.SYNC_BASE_URL)
    registry(SyncForgeHandlers.registry(taskDao))

    conflicts {
        entity("tasks") {
            merge<TaskEntity> { local, remote ->
                local.copy(
                    // Remote wins for title if it was edited more recently
                    title = preferNewer(local.title, remote.title),
                    // Always keep local completion state (device-specific preference)
                    completed = preferLocal(local.completed, remote.completed),
                    // Always take server version counter
                    localVersion = preferRemote(local.localVersion, remote.localVersion),
                    updatedAtMillis = maxOf(local.updatedAtMillis, remote.updatedAtMillis),
                    syncState = SyncState.SYNCED,
                )
            }
        }
    }
}
```

### `MergeScope` helpers

| Helper | Behaviour |
|--------|-----------|
| `preferNewer(local, remote)` | Picks value from whichever side has the newer `updatedAtMillis` |
| `preferLocal(local, remote)` | Always keeps local value |
| `preferRemote(local, remote)` | Always takes remote value |

### Notes

- The lambda receives fully deserialized `local` and `remote` entities.
- Return the merged entity; SyncForge writes it to Room and marks `SYNCED`.
- Merges run at **pull time** — no user prompt needed.
- For irreconcilable edits on the same field, use `deferToUser()` instead.

---

## `:sample` conflict matrix (1.2)

Reference wiring for three entity types on one `SyncManager` — copy from
[`SampleConflictPolicies.kt`](../sample/src/main/kotlin/dev/syncforge/sample/conflicts/SampleConflictPolicies.kt)
or call `sampleEntityConflicts()` from `SampleApplication`:

| Entity | Strategy | Behaviour |
|--------|----------|-----------|
| **notes** | `alwaysRemote()` | Server-owned content; device accepts remote on pull |
| **tags** | `lastWriteWins()` | Simple label rows; newest `updatedAtMillis` wins |
| **tasks** | `gitLike { }` | Title and `completed` merge independently; same-field clash or remote delete → `deferToUser()` |

```kotlin
import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.sample.conflicts.sampleEntityConflicts

@OptIn(ExperimentalSyncForgeApi::class)
SyncForge.android(context) {
    baseUrl(BuildConfig.SYNC_BASE_URL)
    registry(SyncForgeHandlers.registry(noteDao, tagDao, taskDao))
    conflicts {
        sampleEntityConflicts()
    }
}
```

**Tasks `gitLike` policy** (abbreviated — full `threeWayMerge` in `SampleConflictPolicies.kt`):

```kotlin
entity("tasks") {
    gitLike<TaskEntity> {
        threeWayMerge { base, local, remote ->
            // Non-overlapping title vs completed edits → Merged(...)
            // Same field edited on both sides → Unmergeable
        }
        onUnmergeable { deferToUser() }
        onRemoteDelete { deferToUser() }  // delete-conflict E2E + resolution sheet
    }
}
```

**Demo flows in `:sample`:**

- Server edit + local checkbox toggle → auto-merge (no conflict sheet)
- Server delete + local edit → `deferToUser()` → **Resolve** sheet (Keep local / Accept remote)

`:mock-server` rejects stale `UPDATE` pushes after `POST /dev/simulate-edit` so the server edit survives until pull (optimistic concurrency on `localVersion`).

---

## Hierarchical parent/child sync

Parent/child and FK relationships are **app-owned** — SyncForge syncs each `entityType` as flat
rows. Full guide: [HIERARCHICAL_SYNC.md](HIERARCHICAL_SYNC.md).

### Optional FK (notes → tags) — `:sample` pattern

```kotlin
// NoteEntity.kt — tagId is optional; tags sync on a separate handler
data class NoteEntity(
    @PrimaryKey override val id: String,
    val title: String,
    val tagId: String? = null,
    // ...
)

conflicts {
    entity("tags") { lastWriteWins() }
    entity("notes") { alwaysRemote() }
}
```

Register both handlers on one registry — `sync()` pushes/pulls both types; **no guaranteed
parent-before-child order** within a single cycle.

### Orphan policy (pick one or combine)

| Policy | Where | When |
|--------|-------|------|
| **Soft-delete parent** | Parent entity + server | Avoid hard tombstone while children exist |
| **Server `VALIDATION`** | `SyncStore.push` | Reject child when `tagId` missing — see [REST_API.md](REST_API.md) |
| **Client cleanup** | After `sync()` | Null out orphan FKs locally (optionally enqueue update to replicate) |

```kotlin
// Server — reject orphan note push (see HIERARCHICAL_SYNC.md recipe 3)
rejected += PushRejectionDto(
    outboxId = entry.id,
    code = "VALIDATION",
    message = "tagId $tagId does not exist",
)
```

```kotlin
// Client — post-sync reconcile (recipe 4)
suspend fun reconcileOrphanNoteTags(noteDao: NoteDao, tagDao: TagDao) {
    val valid = tagDao.getAllIds().toSet()
    noteDao.getAll().forEach { note ->
        note.tagId?.takeUnless { it in valid }?.let {
            noteDao.update(note.copy(tagId = null))
        }
    }
}
```

### Explicit limitations

SyncForge does **not** cascade deletes, validate FKs on enqueue, or order cross-type outbox
entries. See [BEST_PRACTICES.md § Explicit limitations](BEST_PRACTICES.md#explicit-limitations-hierarchical-data).

---

## Handle `deferToUser()` conflicts in Compose

Use when the user must choose between local and remote versions — e.g. editing the same
field on two devices.

### 1. Configure the strategy

```kotlin
conflicts {
    entity("tasks") { deferToUser() }
}
```

### 2. Observe open conflicts

```kotlin
val conflicts: StateFlow<List<ConflictSummary>> =
    syncManager.conflicts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```

`ConflictSummary` contains `entityType`, `entityId`, `detectedAtMillis` — enough to drive UI.

### 3. Load snapshots for the resolution sheet

```kotlin
fun showConflictSheet(conflict: ConflictSummary) {
    viewModelScope.launch {
        _activeConflict.value = conflict
        val record = syncManager.findOpenConflict(conflict.entityType, conflict.entityId)
        _remotePreview.value = record?.remoteJson?.let {
            json.decodeFromString<TaskEntity>(it)
        }
    }
}
```

Local data comes from your Room `Flow` (already on screen). Remote comes from `ConflictRecord.remoteJson`.

### 4. Resolve the conflict

```kotlin
fun resolveKeepLocal() {
    val conflict = _activeConflict.value ?: return
    viewModelScope.launch {
        syncManager.resolveConflict(
            entityType = conflict.entityType,
            entityId = conflict.entityId,
            choice = ConflictChoice.KeepLocal,
        )
        dismissConflictSheet()
    }
}

fun resolveAcceptRemote() {
    val conflict = _activeConflict.value ?: return
    viewModelScope.launch {
        syncManager.resolveConflict(
            entityType = conflict.entityType,
            entityId = conflict.entityId,
            choice = ConflictChoice.AcceptRemote,
        )
        dismissConflictSheet()
    }
}
```

`KeepLocal` re-queues the local version for push. `AcceptRemote` overwrites Room with the server copy.

### 5. Compose UI (built-in components)

```kotlin
@Composable
fun TasksScreen(viewModel: TasksViewModel) {
    val conflicts by viewModel.conflicts.collectAsState()
    val activeConflict by viewModel.activeConflict.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tasks") },
                actions = {
                    SyncConflictChip(
                        count = conflicts.size,
                        onClick = viewModel::openFirstConflict,
                    )
                },
            )
        },
    ) { /* list content */ }

    SyncConflictResolutionSheet(
        conflict = activeConflict,
        localContent = { TaskPreview(viewModel.localTaskFor(activeConflict)) },
        remoteContent = { TaskPreview(viewModel.remotePreview) },
        onKeepLocal = viewModel::resolveKeepLocal,
        onAcceptRemote = viewModel::resolveAcceptRemote,
        onDismiss = viewModel::dismissConflictSheet,
    )
}
```

### Custom merged resolution

For a three-way merge chosen by the user:

```kotlin
syncManager.resolveConflict(
    entityType = "tasks",
    entityId = taskId,
    choice = ConflictChoice.Custom(mergedTask),
)
```

---

## Use the in-app debug console

The debug console (Chucker/Hyperion-style) inspects outbox rows, conflicts, sync health,
and recent events. **Enable only in debug builds.**

### Wrap your root composable

```kotlin
SyncDebugLauncher(
    syncManager = syncManager,
    enabled = BuildConfig.DEBUG,
) {
    MyAppNavHost()
}
```

Tap the floating **SF** button to open the panel.

### Release / support diagnostics (read-only)

For non-debug builds, embed the dashboard without outbox payloads or destructive actions:

```kotlin
SyncHealthDiagnosticScreen(syncManager = syncManager, onDismiss = { /* pop back */ })

// Or via launcher overlay:
SyncDebugLauncher(
    syncManager = syncManager,
    enabled = BuildConfig.FLAVOR == "internal",
    useFullScreenDiagnostic = true,
) { MyApp() }

// Bottom-sheet read-only overview:
SyncDebugLauncher(
    syncManager = syncManager,
    panelMode = SyncDebugPanelMode.DIAGNOSTIC,
) { MyApp() }
```

### Panel tabs

| Tab | What you see |
|-----|--------------|
| **Overview** | `SyncHealthDashboard` — status banner, outbox/conflict tiles, error breakdown, latency bars |
| **Outbox** | Every outbox row with entity type, change type, retry count |
| **Conflicts** | Open and resolved conflict records with JSON snapshots |
| **History** | Ring buffer of last 100 sync events (push, pull, enqueue, errors) |

### Manual actions

From the panel action bar:

- **Sync** — full push + pull cycle
- **Push** — flush outbox only
- **Pull** — fetch remote deltas only
- **Clear outbox** — removes all outbox rows (does not roll back Room writes)

### Programmatic access

For custom debug screens:

```kotlin
val health by syncManager.debug.health.collectAsState()
val outbox by syncManager.debug.outboxItems.collectAsState()
val events by syncManager.debug.events.collectAsState()
val conflicts by syncManager.debug.conflictRecords.collectAsState()
```

```kotlin
// Clear logs during QA
syncManager.debug.clearEventLog()
syncManager.debug.clearOutbox()  // destructive — use with care
```

---

## Observe sync status in Compose

### Quick banner

```kotlin
@Composable
fun SyncStatusBanner(syncManager: SyncManager) {
    val uiModel = syncManager.collectSyncStatusUiModel()
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (uiModel.isSyncing) CircularProgressIndicator(Modifier.size(16.dp))
        Text(uiModel.label)
    }
}
```

### ViewModel pattern

```kotlin
val syncStatus: StateFlow<SyncStatusUiModel> =
    syncManager.status
        .map { it.toUiModel() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
```

### Raw `SyncStatus` for custom UI

```kotlin
val status by syncManager.status.collectAsState()

when (val s = status) {
    is SyncStatus.Pending -> {
        // s.outboxCount, s.permanentlyFailedCount, s.conflictCount
    }
    is SyncStatus.Offline -> { /* s.outboxCount */ }
    is SyncStatus.Error -> { /* s.message, s.retryable */ }
    is SyncStatus.Syncing -> { /* s.phase: PUSH, PULL, FULL */ }
    else -> { }
}
```

### Per-row sync indicator

Show `SyncState` from your entity:

```kotlin
when (task.syncState) {
    SyncState.SYNCED -> Icon(Icons.Default.CloudDone, "Synced")
    SyncState.PENDING -> Icon(Icons.Default.CloudUpload, "Pending")
    SyncState.CONFLICT -> Icon(Icons.Default.Warning, "Conflict")
    SyncState.FAILED -> Icon(Icons.Default.CloudOff, "Failed")
}
```

---

## Observe conflicts alongside status

Combine status and conflicts for a unified toolbar:

```kotlin
val syncStatus by syncManager.status.map { it.toUiModel() }.collectAsState(initial)
val conflicts by syncManager.conflicts.collectAsState()

val hasWork = syncStatus.pendingCount > 0 || conflicts.isNotEmpty()

Badge(enabled = hasWork) {
    IconButton(onClick = onSync) { Icon(Icons.Default.Sync, "Sync") }
}
```

Status label already includes conflict count when using `toUiModel()`:

```
"3 changes pending · 1 conflict"
"1 conflict needs resolution"   // when outbox is empty but conflicts remain
```

---

## Trigger sync from different layers

| Layer | How |
|-------|-----|
| UI button | `viewModelScope.launch { syncManager.sync() }` |
| Repository | `suspend fun sync() = syncManager.sync()` |
| After login | `syncManager.sync()` once auth token is available |
| Background | `schedulePeriodicSyncOnStart()` in `SyncForge.android { }` |
| Manual push only | `syncManager.push()` |
| Manual pull only | `syncManager.pull()` |

---

## BYO entity store (`@SyncForgeStore`)

Use when Room is not your persistence layer, or when you want every entity type to go through
`EntityStore<T>` (Room included via the optional adapter).

Full walkthrough: [Getting Started → Path B](GETTING_STARTED.md#path-b--bring-your-own-store-syncforgestore).

### When to use

| Scenario | Approach |
|----------|----------|
| Realm / SQLDelight / custom DB | Implement `EntityStore<T>`, `@SyncForgeStore` |
| Room but unified store API | `RoomEntityStore` from `syncforge-store-room` |
| JVM/common unit tests | `InMemoryEntityStore` from `syncforge-store-inmemory` |
| Quick start with Room DAOs | Stay on `@SyncForgeDao` (Path A) — no change |

### Dependencies

```kotlin
// app/build.gradle.kts — Room BYO-store bridge (Android)
implementation("studio.syncforge:syncforge-store-room:1.1.0")

// tests — non-Room stack
testImplementation("studio.syncforge:syncforge-store-inmemory:1.1.0")
```

### Custom store

```kotlin
@SyncForgeStore(entityClass = "com.example.tasks.TaskEntity")
class TaskEntityStore(
    private val backend: TaskBackend,
) : EntityStore<TaskEntity> {
    override suspend fun findById(id: String): TaskEntity? = backend.load(id)
    override suspend fun upsert(entity: TaskEntity) { backend.save(entity) }
    override suspend fun delete(id: String) { backend.remove(id) }
}
```

### Room via `RoomEntityStore`

```kotlin
@SyncForgeStore(entityClass = "com.example.tasks.TaskEntity")
class TaskEntityStore(dao: TaskDao, db: AppDatabase) : RoomEntityStore<TaskEntity>(dao, db)

// Application
registry(SyncForgeHandlers.registry(TaskEntityStore(db.taskDao(), db)))
```

### In-memory test stack

```kotlin
@SyncForgeStore(entityClass = "com.example.tasks.TaskEntity")
class TaskEntityStore : InMemoryEntityStore<TaskEntity>()

@Test
fun enqueue_appliesOptimistic() = runTest {
    val store = TaskEntityStore()
    val handler = /* KSP-generated or test double */
    // … see syncforge-store-inmemory tests
}
```

### Gradle — skip Room KSP

When sources have no `@SyncForgeDao` / `androidx.room`, the Android plugin skips Room compiler
automatically. Override explicitly:

```kotlin
syncForge {
    roomCodegen = false
}
```

### Registry rule

One binding per entity: **`@SyncForgeDao` XOR `@SyncForgeStore`**. Mixed registries are fine
(e.g. `tasks` via Room DAO, `notes` via `@SyncForgeStore`).

---

## Dependency injection (Koin + Hilt)

`:syncforge` has **no** Koin or Dagger dependency. Wire `SyncManager`, repositories, and WorkManager
in your app module — same shape as [`:sample`](../sample/src/main/kotlin/dev/syncforge/sample/SampleApplication.kt).

Optional helpers:

| Artifact | Role |
|----------|------|
| `syncforge-integration-koin` | `syncForgeModule { }`, `syncForgeWorkManagerConfiguration()` |
| `syncforge-integration-hilt` | `SyncForgeHilt.createSyncManager`, `SyncForgeHilt.workManagerConfiguration` |

```kotlin
// Published coordinates (optional)
implementation("studio.syncforge:syncforge-integration-koin:1.1.0")
implementation("studio.syncforge:syncforge-integration-hilt:1.1.0")
```

### What to inject (matches `:sample`)

| Binding | Scope | Notes |
|---------|-------|-------|
| `SampleDatabase` / your `@Database` | Singleton | Room DB |
| `TaskDao`, `NoteDao`, … | Singleton | From database |
| `SyncManager` | Singleton | `SyncForge.android { registry(SyncForgeHandlers.registry(...)) }` |
| `TaskRepository`, … | Singleton or factory | Takes DAO + `SyncManager` |
| WorkManager | `Configuration.Provider` | `SyncForgeAndroid.workManagerConfiguration { syncManager }` |

### Manual wiring (no DI framework)

Same as Getting Started — [`SampleApplication`](../sample/src/main/kotlin/dev/syncforge/sample/SampleApplication.kt):

```kotlin
class SampleApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = SyncForgeAndroid.workManagerConfiguration { syncManager }

    override fun onCreate() {
        super.onCreate()
        val db = SampleDatabase.create(this)
        syncManager = SyncForge.android(this) {
            baseUrl(BuildConfig.SYNC_BASE_URL)
            registry(SyncForgeHandlers.registry(db.noteDao(), db.tagDao(), db.taskDao()))
            conflicts {
                entity("notes") { alwaysRemote() }
                entity("tags") { lastWriteWins() }
                entity("tasks") { deferToUser() } // :sample uses gitLike — see SampleConflictPolicies.kt
            }
            schedulePeriodicSyncOnStart()
        }
        taskRepository = TaskRepository(db.taskDao(), syncManager)
        // ...
    }
}
```

### Koin

```kotlin
// build.gradle.kts
dependencies {
    implementation("studio.syncforge:syncforge-integration-koin:1.1.0")
    implementation("io.insert-koin:koin-android:4.0.1")
}
```

```kotlin
import dev.syncforge.integration.koin.syncForgeModule
import dev.syncforge.integration.koin.syncForgeWorkManagerConfiguration
import dev.syncforge.sample.notes.SyncForgeHandlers
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.dsl.module

val databaseModule = module {
    single { SampleDatabase.create(androidContext()) }
    single { get<SampleDatabase>().taskDao() }
    single { get<SampleDatabase>().noteDao() }
    single { get<SampleDatabase>().tagDao() }
}

val syncForgeKoinModule = module {
    single<SyncManager> {
        SyncForge.android(androidContext()) {
            baseUrl(BuildConfig.SYNC_BASE_URL)
            registry(
                SyncForgeHandlers.registry(
                    get(), // NoteDao
                    get(), // TagDao
                    get(), // TaskDao
                ),
            )
            conflicts {
                entity("notes") { alwaysRemote() }
                entity("tags") { lastWriteWins() }
                entity("tasks") { deferToUser() } // :sample uses gitLike — see SampleConflictPolicies.kt
            }
            schedulePeriodicSyncOnStart()
        }
    }
}

val repositoryModule = module {
    single { TaskRepository(get(), get()) }
    single { NoteRepository(get(), get()) }
    single { TagRepository(get(), get()) }
}

class MyApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = syncForgeWorkManagerConfiguration {
            GlobalContext.get().get<SyncManager>()
        }

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MyApplication)
            modules(databaseModule, syncForgeKoinModule, repositoryModule)
        }
    }
}
```

**Shortcut** when `SyncForge.android { }` does not need other Koin `get()` calls inside the block:

```kotlin
val syncModule = syncForgeModule {
    baseUrl(BuildConfig.SYNC_BASE_URL)
    registry(SyncForgeHandlers.registry(noteDao, tagDao, taskDao)) // capture DAOs from outer scope
}
```

Inject `SyncManager` into ViewModels: `class TasksViewModel(private val syncManager: SyncManager, …)`.

### Hilt

```kotlin
// build.gradle.kts
plugins {
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}
dependencies {
    implementation("studio.syncforge:syncforge-integration-hilt:1.1.0")
    implementation("com.google.dagger:hilt-android:2.56.1")
    ksp("com.google.dagger:hilt-android-compiler:2.56.1")
}
```

```kotlin
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.syncforge.integration.hilt.SyncForgeHilt
import dev.syncforge.sample.notes.SyncForgeHandlers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SampleDatabase =
        SampleDatabase.create(context)

    @Provides fun provideTaskDao(db: SampleDatabase): TaskDao = db.taskDao()
    @Provides fun provideNoteDao(db: SampleDatabase): NoteDao = db.noteDao()
    @Provides fun provideTagDao(db: SampleDatabase): TagDao = db.tagDao()
}

@Module
@InstallIn(SingletonComponent::class)
object SyncForgeModule {

    @Provides
    @Singleton
    fun provideSyncManager(
        @ApplicationContext context: Context,
        noteDao: NoteDao,
        tagDao: TagDao,
        taskDao: TaskDao,
    ): SyncManager = SyncForgeHilt.createSyncManager(context) {
        baseUrl(BuildConfig.SYNC_BASE_URL)
        registry(SyncForgeHandlers.registry(noteDao, tagDao, taskDao))
        conflicts {
            entity("notes") { alwaysRemote() }
            entity("tags") { lastWriteWins() }
            entity("tasks") { deferToUser() } // :sample uses gitLike — see SampleConflictPolicies.kt
        }
        schedulePeriodicSyncOnStart()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides @Singleton
    fun provideTaskRepository(dao: TaskDao, syncManager: SyncManager): TaskRepository =
        TaskRepository(dao, syncManager)

    @Provides @Singleton
    fun provideNoteRepository(dao: NoteDao, syncManager: SyncManager): NoteRepository =
        NoteRepository(dao, syncManager)

    @Provides @Singleton
    fun provideTagRepository(dao: TagDao, syncManager: SyncManager): TagRepository =
        TagRepository(dao, syncManager)
}
```

```kotlin
@HiltAndroidApp
class MyApplication : Application(), Configuration.Provider {

    @Inject lateinit var syncManager: SyncManager

    override val workManagerConfiguration: Configuration
        get() = SyncForgeHilt.workManagerConfiguration { syncManager }
}
```

Use `@AndroidEntryPoint` on Activities and `@HiltViewModel` + constructor injection for ViewModels.

### WorkManager rule

Background sync requires `SyncWorkerFactory` so `SyncWorker` receives your app’s `SyncManager`.
All patterns above delegate to `SyncForgeAndroid.workManagerConfiguration` — do **not** use the default
WorkManager initializer without a custom `WorkerFactory`.

### Optional `:sample-di` fork (D4)

`:sample` uses manual `Application` wiring for clarity. To try Koin/Hilt end-to-end, copy the modules
above into a sibling `sample-koin` or `sample-hilt` app module — same entities/DAOs as `:sample`.

---

## Inject app-owned Ktor HttpClient

Use when your app already runs Ktor — shared OkHttp/Darwin engine, request logging, tracing,
or custom interceptors. SyncForge uses the client **only** for `/sync/push` and `/sync/pull`;
all other API traffic can share the same engine.

### Default (no injection)

Android apps using `id("studio.syncforge.android")` get `syncforge-network-ktor` automatically.
Omit `httpClient()` and SyncForge creates a platform client (OkHttp on Android, Darwin on iOS).

### Basic injection (Android)

```kotlin
import dev.syncforge.network.KtorSyncTransport
import dev.syncforge.network.buildSyncForgeHttpClient
import io.ktor.client.engine.okhttp.OkHttp

class MyApplication : Application() {

    private val syncHttpClient by lazy {
        buildSyncForgeHttpClient(
            engine = OkHttp.create(),
            auth = null,  // set SyncAuthProvider when using authToken { } — see below
            json = KtorSyncTransport.defaultJson,
        )
    }

    override fun onCreate() {
        super.onCreate()
        syncManager = SyncForge.android(this) {
            baseUrl("https://api.example.com")
            httpClient(syncHttpClient)
            registry(SyncForgeHandlers.registry(taskDao))
        }
    }
}
```

`buildSyncForgeHttpClient` adds JSON negotiation, non-2xx → `SyncTransportException`, and optional
bearer `defaultRequest` when you pass a `SyncAuthProvider`.

### Shared engine with your other APIs

```kotlin
// One engine — sync + REST API share connection pool / TLS config
private val httpEngine = OkHttp.create()

val syncClient = buildSyncForgeHttpClient(httpEngine, authProvider, KtorSyncTransport.defaultJson)

val apiClient = HttpClient(httpEngine) {
    install(ContentNegotiation) { json(KtorSyncTransport.defaultJson) }
    // your app-specific plugins (cookies, tracing, etc.)
}

SyncForge.android(this) {
    baseUrl(BuildConfig.SYNC_BASE_URL)
    httpClient(syncClient)
    registry(SyncForgeHandlers.registry(taskDao))
}
```

### Request logging (optional)

Add Ktor's logging plugin to **your** client build (not a SyncForge dependency):

```kotlin
// build.gradle.kts — app module
dependencies {
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
}
```

```kotlin
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging

val syncClient = HttpClient(OkHttp.create()) {
    install(ContentNegotiation) { json(KtorSyncTransport.defaultJson) }
    install(Logging) {
        level = LogLevel.HEADERS  // BODY in debug only — may log tokens
    }
    // Mirror buildSyncForgeHttpClient: HttpResponseValidator + defaultRequest for auth
}
```

Prefer `HEADERS` or a redacting interceptor in production — sync payloads may contain entity data.

### Bearer auth with an injected client

`authToken { }` and `auth(SyncAuthProvider…)` configure transport-level 401 refresh, but the
**injected** `HttpClient` must attach the bearer header itself:

```kotlin
val tokenStore = TokenStore()
val authProvider = SyncAuthProvider.bearer { tokenStore.accessToken }

val syncClient = buildSyncForgeHttpClient(
    engine = OkHttp.create(),
    auth = authProvider,
    json = KtorSyncTransport.defaultJson,
)

SyncForge.android(this) {
    baseUrl("https://api.example.com")
    httpClient(syncClient)
    auth(authProvider)  // enables 401 refresh retry on sync transport
    registry(SyncForgeHandlers.registry(taskDao))
}
```

Built-in `auth { }` (register/login) uses a **separate** auth HTTP client for `/auth/*` routes;
sync push/pull still go through `httpClient()` above.

### iOS / desktop

```kotlin
import dev.syncforge.network.ensureSyncForgeNetworkKtorLoaded

// Call once before SyncForge.ios { } when using default transport (no explicit transport())
ensureSyncForgeNetworkKtorLoaded()

SyncForge.ios {
    baseUrl("https://api.example.com")
    httpClient(iosSyncHttpClient)  // optional — Darwin engine via buildSyncForgeHttpClient
    registry(handlers)
}
```

`SyncForge.desktop { httpClient(...) }` follows the same pattern. Requires
`studio.syncforge:syncforge-network-ktor` on the classpath.

### Escape hatch — custom transport

Non-REST backends skip `httpClient()` entirely. See [CUSTOM_TRANSPORT.md](CUSTOM_TRANSPORT.md).

```kotlin
SyncForge.android(this) {
    baseUrl("https://api.example.com")  // still used by built-in auth { } if configured
    transport(MySyncTransport(...))
    registry(handlers)
}
```

---

## Bearer token auth

```kotlin
SyncForge.android(this) {
    baseUrl("https://api.example.com")
    registry(SyncForgeHandlers.registry(taskDao))
    authToken { tokenStore.accessToken }  // or auth(SyncAuthProvider.bearer { ... })
}
```

Token is sent as `Authorization: Bearer <token>` on every push/pull request.

### Token refresh on 401

Use [RefreshingSyncAuthProvider](../syncforge/src/commonMain/kotlin/dev/syncforge/network/RefreshingSyncAuthProvider.kt) when access tokens expire. `KtorSyncTransport` calls your refresh lambda once per failed request and retries with the updated token.

```kotlin
val store = TokenStore()

SyncForge.android(this) {
    baseUrl("https://api.example.com")
    registry(SyncForgeHandlers.registry(taskDao))
    auth(
        SyncAuthProvider.refreshing(
            accessTokenProvider = { store.accessToken },
            refresh = {
                val tokens = oauthClient.refresh(store.refreshToken)
                store.update(tokens)
                store.accessToken
            },
        ),
    )
}
```

Requirements:

- `accessTokenProvider` must read the **current** token (same store `refresh` writes to).
- Refresh is serialized — parallel syncs share one in-flight refresh.
- Only **401** triggers refresh; **403** and other auth errors are not retried.
- Each request is retried at most **once** after refresh.

Works on all platforms (`SyncForge.android`, `SyncForge.ios`, `SyncForge.desktop`).

When using an injected `HttpClient`, pass the same `SyncAuthProvider` into
`buildSyncForgeHttpClient` **and** `auth(…)` so headers and 401 refresh stay aligned —
see [Inject app-owned Ktor HttpClient](#inject-app-owned-ktor-httpclient).

---

## Test without a backend

```kotlin
SyncForge.builder {
    handler(taskHandler)
    outbox = InMemoryOutboxRepository()
    transport = NoOpSyncTransport()
    scope = testScope
}.also { syncManager ->
    syncManager.enqueueChange(Change.create("tasks", task))
    syncManager.push()  // acknowledges immediately
}
```

Use `SyncForge.builder { }` in `commonTest` — no Android context required.

---

## GraphQL sync transport (client)

Use when your backend already exposes GraphQL and you want the same push/pull semantics as REST
without changing entity handlers or KSP codegen.

### Dependencies

```kotlin
implementation("studio.syncforge:syncforge-transport-graphql:1.2.0")
```

### Wire the transport

```kotlin
import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.transport.graphql.GraphQlSyncConfig
import dev.syncforge.transport.graphql.GraphQlSyncTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

@OptIn(ExperimentalSyncForgeApi::class)
val graphqlHttpClient = HttpClient(OkHttp.create()) {
    install(ContentNegotiation) {
        json(dev.syncforge.transport.graphql.KtorGraphQlSyncApi.defaultJson)
    }
}

SyncForge.android(this) {
    transport(
        GraphQlSyncTransport(
            config = GraphQlSyncConfig(
                endpointUrl = "http://10.0.2.2:8080/graphql",
                bearerToken = { tokenStore.accessToken },
            ),
            httpClient = graphqlHttpClient,
        ),
    )
    registry(SyncForgeHandlers.registry(taskDao))
    // baseUrl() omitted — GraphQL transport owns the endpoint
}
```

Operations map 1:1 to REST (`syncPush` mutation, `syncPull` query). See
[syncforge-server/graphql/syncforge-sync.graphql](../syncforge-server/graphql/syncforge-sync.graphql).

### Local reference server

```bash
./gradlew :backend-starter-graphql:run   # GraphQL only
./gradlew :mock-server:run               # REST + GraphQL + conflict demos
```

---

## GraphQL server schema and resolvers

Expose **two** operations with the same semantics as [REST_API.md](REST_API.md) — not per-entity
CRUD. `payloadJson` stays an opaque string.

### Schema (copy into your GraphQL server)

Full SDL: [syncforge-server/graphql/syncforge-sync.graphql](../syncforge-server/graphql/syncforge-sync.graphql)

```graphql
scalar Long

enum ChangeType { CREATE UPDATE DELETE }

input OutboxEntryInput {
  id: Long!
  entityType: String!
  entityId: String!
  changeType: ChangeType!
  payloadJson: String
  localVersion: Long!
  createdAtMillis: Long!
}

type PushPayload {
  acknowledgedIds: [Long!]!
  rejected: [PushRejection!]!
}

type PullPayload {
  deltas: [RemoteDelta!]!
  serverTimestampMillis: Long!
  hasMore: Boolean!
  nextPageCursor: String
}

type Query {
  syncPull(since: Long!, types: [String!]!, limit: Int, cursor: String): PullPayload!
}

type Mutation {
  syncPush(entries: [OutboxEntryInput!]!): PushPayload!
}
```

### Ktor resolver (self-hosted)

`:syncforge-server` delegates to `SyncHandlers` — same store as REST:

```kotlin
import dev.syncforge.server.graphqlRoutes
import dev.syncforge.server.syncRoutes

routing {
    syncRoutes(mySyncStore)      // optional REST
    graphqlRoutes(mySyncStore)   // POST /graphql
}
```

Runnable starter: `./gradlew :backend-starter-graphql:run`

Apollo Server and Spring GraphQL sketches: [syncforge-server/graphql/README.md](../syncforge-server/graphql/README.md).

---

## BYO `SyncDeltaStore` (BaaS / hosted backend)

Full guide: [CUSTOM_TRANSPORT.md → BYO SyncDeltaStore](CUSTOM_TRANSPORT.md#byo-syncdeltastore).

Use when the client talks to Firebase, Supabase, or a custom RPC/NoSQL store instead of REST or
GraphQL. Implement the storage port once; `DeltaStoreSyncTransport` maps it to `SyncTransport`.

### Dependencies

```kotlin
implementation("studio.syncforge:syncforge-transport-core:1.2.0")
// optional ready-made impls:
implementation("studio.syncforge:syncforge-transport-supabase:1.2.0")
implementation("studio.syncforge:syncforge-transport-firebase:1.2.0")
```

### Ready-made stores

```kotlin
@OptIn(ExperimentalSyncForgeApi::class)
SyncForge.android(this) {
    transport(DeltaStoreSyncTransport(SupabaseSyncDeltaStore(config)))
    // or: DeltaStoreSyncTransport(FirebaseSyncDeltaStore(config))
    registry(SyncForgeHandlers.registry(taskDao))
}
```

See [syncforge-transport-supabase](../syncforge-transport-supabase/README.md) and
[syncforge-transport-firebase](../syncforge-transport-firebase/README.md).

### Custom store (~100–200 lines)

```kotlin
@OptIn(ExperimentalSyncForgeApi::class)
class MyRpcSyncDeltaStore(
    private val api: MyBackendApi,
) : SyncDeltaStore {

    override suspend fun appendEntries(entries: List<OutboxEntry>): PushResult {
        val response = api.push(entries.map { it.toDto() })
        return response.toPushResult()
    }

    override suspend fun queryDeltas(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        pageSize: Int,
        pageCursor: String?,
    ): PullResult {
        val response = api.pull(sinceTimestampMillis, entityTypes, pageSize, pageCursor)
        return response.toPullResult()
    }
}

SyncForge.android(this) {
    transport(DeltaStoreSyncTransport(MyRpcSyncDeltaStore(api)))
    registry(handlers)
}
```

Contract test kit: implement `SyncDeltaStoreContract` scenarios in `commonTest` (see
[transport-core README](../syncforge-transport-core/README.md)).

---

## Custom `SyncTransport`

Full guide: [CUSTOM_TRANSPORT.md](CUSTOM_TRANSPORT.md) (decision tree, errors, auth, testing).

Use when the wire format is neither REST (`KtorSyncTransport`) nor a shipped adapter (GraphQL,
Supabase, Firebase). `SyncManager` only requires `push()` / `pull()`.

### Minimal skeleton

```kotlin
class MySyncTransport(
    private val backend: MyBackendClient,
) : SyncTransport {

    override suspend fun push(entries: List<OutboxEntry>): PushResult {
        val response = backend.uploadChanges(entries.map { it.toDto() })
        return response.toPushResult()
    }

    override suspend fun pull(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        pageSize: Int,
        pageCursor: String?,
    ): PullResult {
        val response = backend.downloadChanges(sinceTimestampMillis, entityTypes, pageSize, pageCursor)
        return response.toPullResult()
    }
}

SyncForge.android(this) {
    transport(MySyncTransport(backend))
    registry(handlers)
}
```

Reuse `OutboxEntry.toDto()`, `PushResponse.toPushResult()`, and `PullResponse.toPullResult()` from
`dev.syncforge.network.api` when your backend shares REST DTO shapes.

### Shipped alternatives

| Backend | Transport |
|---------|-----------|
| REST / Ktor | `KtorSyncTransport` (default) |
| GraphQL | `GraphQlSyncTransport` — [GraphQL sync transport](#graphql-sync-transport-client) |
| Supabase / Firebase | `DeltaStoreSyncTransport` + vendor `SyncDeltaStore` |

---

## Simulate conflicts locally (mock server)

With `:mock-server` running:

```kotlin
// POST /dev/simulate-edit — entity must exist on server first (push before simulating)
DevSyncClient.simulateServerEdit(task, newTitle = "${task.title} (server)")
// Then edit locally and call syncManager.sync()
```

See [REST API → simulate-edit](REST_API.md#post-devsimulate-edit-mock-server-only).