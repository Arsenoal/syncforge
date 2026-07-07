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

### Panel tabs

| Tab | What you see |
|-----|--------------|
| **Overview** | `SyncHealth` — online status, pending/failed counts, open conflicts, cursor |
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

Non-REST backends skip `httpClient()` entirely:

```kotlin
SyncForge.android(this) {
    baseUrl("https://api.example.com")  // still used by built-in auth { } if configured
    transport(MyGraphqlSyncTransport(...))
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

## Simulate conflicts locally (mock server)

With `:mock-server` running:

```kotlin
// POST /dev/simulate-edit — entity must exist on server first (push before simulating)
DevSyncClient.simulateServerEdit(task, newTitle = "${task.title} (server)")
// Then edit locally and call syncManager.sync()
```

See [REST API → simulate-edit](REST_API.md#post-devsimulate-edit-mock-server-only).