# Feature catalog

Every major SyncForge capability with a minimal copy-paste sample. Platform Gradle setup lives in
[ANDROID_SETUP.md](ANDROID_SETUP.md), [IOS_SETUP.md](IOS_SETUP.md), [DESKTOP_SETUP.md](DESKTOP_SETUP.md),
and [WEB_SETUP.md](WEB_SETUP.md). Deep dives: [RECIPES.md](RECIPES.md) · [MODULES.md](MODULES.md).

| Area | What you get | Sample module / doc |
|------|----------------|---------------------|
| **Core sync** | Outbox, push/pull, optimistic writes | `:sample` · [REST API](REST_API.md) |
| **Android DSL** | SQLDelight outbox, WorkManager, Compose UI | `:sample` · [Android setup](ANDROID_SETUP.md) |
| **iOS / desktop / macOS** | Stable KMP DSLs, BGTaskScheduler, JVM desktop | `:sample-ios-shared`, `:sample-desktop` · [iOS](IOS_SETUP.md) · [Desktop](DESKTOP_SETUP.md) |
| **Web (experimental)** | `SyncForge.web { }`, Ktor JS transport | `:sample-web` · [Web setup](WEB_SETUP.md) |
| **Conflict strategies** | LWW, defer, merge, `gitLike`, `crdt`, runtime policy | `:sample` · [Conflict resolution](CONFLICT_RESOLUTION.md) |
| **Entity store** | Room DAO, `@SyncForgeStore`, in-memory tests | [Recipes → BYO store](RECIPES.md#byo-entity-store-syncforgestore) |
| **Auth** | Built-in register/login/refresh, bearer tokens | [Auth API](AUTH_API.md) |
| **DI** | Koin + Hilt helpers | [Recipes → DI](RECIPES.md#dependency-injection-koin--hilt) |
| **Transports** | REST (default), GraphQL, Supabase, Firebase, custom | `:mock-server`, `:backend-starter-*` · [Custom transport](CUSTOM_TRANSPORT.md) |
| **Observability** | Debug console, SyncHealth, OpenTelemetry | [Tracing](TRACING.md) · [Recipes → debug](RECIPES.md#use-the-in-app-debug-console) |
| **Hierarchical data** | Parent/child FK recipes | [Hierarchical sync](HIERARCHICAL_SYNC.md) |
| **Backend** | Ktor, Spring Boot, GraphQL reference servers | `:mock-server`, `:backend-starter-spring` |

---

## Core sync loop

Your UI writes to **your** database; SyncForge owns a separate outbox. Mutations enqueue; sync drains the outbox and applies remote deltas.

```kotlin
taskDao.insert(task.copy(syncState = SyncState.PENDING))
syncManager.enqueueChange(Change.create(TaskEntity.ENTITY_TYPE, task))
syncManager.sync()  // or push() / pull() separately
```

Runnable backend locally: `./gradlew :mock-server:run` then point `baseUrl` at `http://10.0.2.2:8080` (emulator).

---

## Platform entry points

**Android** — [`SampleApplication.kt`](../sample/src/main/kotlin/dev/syncforge/sample/SampleApplication.kt):

```kotlin
syncManager = SyncForge.android(this) {
    baseUrl(BuildConfig.SYNC_BASE_URL)
    registry(SyncForgeHandlers.registry(noteDao, tagDao, taskDao))
    conflicts { sampleEntityConflicts() }
    schedulePeriodicSyncOnStart()
}
```

**iOS** — [`IosSampleController.kt`](../sample-ios-shared/src/iosMain/kotlin/dev/syncforge/sample/ios/IosSampleController.kt):

```kotlin
SyncForge.ios {
    baseUrl(baseUrl)
    registry(handlers)
    backgroundSyncTaskIdentifier("com.myapp.sync.refresh")
    schedulePeriodicSyncOnStart()
}
```

**JVM desktop** — [`DesktopSampleController.kt`](../sample-desktop/src/main/kotlin/dev/syncforge/sample/desktop/DesktopSampleController.kt):

```kotlin
SyncForge.desktop {
    baseUrl(baseUrl)
    registry(EntityRegistry.of(taskHandler, noteHandler, tagHandler))
    databaseName("my-app-sync.db")
    schedulePeriodicSyncOnStart()
}
```

**Browser (experimental, monorepo-only)** — [`WebSampleController.kt`](../sample-web/src/jsMain/kotlin/dev/syncforge/sample/web/WebSampleController.kt):

```kotlin
SyncForge.web {
    baseUrl(baseUrl)
    registry(handlers)
    syncOnTabVisible()
}
```

---

## Entities & KSP codegen

```kotlin
@SyncForgeEntity(entityType = "tasks")
@Entity(tableName = "tasks")
@Serializable
data class TaskEntity(/* id, fields, localVersion, updatedAtMillis, syncState */) : SyncedEntity

@SyncForgeDao(entityClass = "com.example.tasks.TaskEntity")
@Dao
interface TaskDao { /* findById, insert, update, deleteById, observeAll */ }
```

**BYO store** — `@SyncForgeStore` + optional `syncforge-store-room` / `syncforge-store-inmemory`:

```kotlin
@SyncForgeStore(entityClass = "com.example.tasks.TaskEntity")
class TaskEntityStore(dao: TaskDao, db: AppDatabase) : RoomEntityStore<TaskEntity>(dao, db)

registry(SyncForgeHandlers.registry(taskEntityStore))
```

---

## Conflict resolution

Per-entity policies in `conflicts { }`. Reference matrix from [`SampleConflictPolicies.kt`](../sample/src/main/kotlin/dev/syncforge/sample/conflicts/SampleConflictPolicies.kt):

| Strategy | Sample use | Snippet |
|----------|------------|---------|
| `lastWriteWins()` | Simple rows (tags) | `entity("tags") { lastWriteWins() }` |
| `alwaysRemote()` | Server-owned content (notes) | `entity("notes") { alwaysRemote() }` |
| `deferToUser()` | User picks winner in UI | `entity("tasks") { deferToUser() }` |
| `merge { }` | Field-level auto-merge | [Recipes → merge](RECIPES.md#custom-merge-with-merge--) |
| `gitLike { }` | Independent field merges + defer on clash | Below |
| `crdt { }` | CRDT field merge | [Conflict resolution](CONFLICT_RESOLUTION.md) |

```kotlin
entity("tasks") {
    gitLike<TaskEntity> {
        threeWayMerge { base, local, remote -> /* Merged or Unmergeable */ }
        onUnmergeable { deferToUser() }
        onRemoteDelete { deferToUser() }
    }
}

syncManager.updateConflictPolicy(conflictPolicyFromSampleKinds(userSelectedKinds))
```

Compose UI: [`TasksScreen.kt`](../sample/src/main/kotlin/dev/syncforge/sample/tasks/TasksScreen.kt) · [Recipes → deferToUser](RECIPES.md#handle-defertouser-conflicts-in-compose).

---

## Authentication

```kotlin
SyncForge.android(this) {
    baseUrl("https://api.example.com")
    auth {
        registerPath("/auth/register")
        loginPath("/auth/login")
        refreshPath("/auth/refresh")
        tokenStore(encryptedTokenStore)
    }
    registry(SyncForgeHandlers.registry(taskDao))
}

syncManager.login(email, password)
authToken { tokenStore.accessToken }  // or SyncAuthProvider.refreshing { ... }
```

Full API: [AUTH_API.md](AUTH_API.md).

---

## Background sync & scheduling

```kotlin
SyncForge.android(this) {
    schedulePeriodicSyncOnStart()
    minSyncInterval(Duration.parse("PT5M"))
    backoffPolicy(SyncBackoffPolicy.exponential())
}

override val workManagerConfiguration: Configuration
    get() = SyncForgeAndroid.workManagerConfiguration { syncManager }
```

---

## UI & observability

```kotlin
syncManager.status.collectSyncStatusUiModel().collectAsState()
syncManager.conflicts.observeOpenConflicts().collectAsState(emptyList())
SyncDebugLauncher.attach(activity, syncManager)
```

OpenTelemetry: [TRACING.md](TRACING.md) · Audit export: [AUDIT_EXPORT.md](AUDIT_EXPORT.md).

---

## Ecosystem transports

```kotlin
implementation(syncforge.transport.graphql)
transport(GraphQlSyncTransport(GraphQlSyncConfig(endpointUrl = "https://api.example.com/graphql"), httpClient))

implementation(syncforge.transport.supabase)
transport(DeltaStoreSyncTransport(SupabaseSyncDeltaStore(config)))

transport(MySyncTransport(...))
```

Reference servers: `./gradlew :mock-server:run` · `:backend-starter:run` · `:backend-starter-spring:bootRun` · `:backend-starter-graphql:run`

---

## Dependency injection

```kotlin
implementation(syncforge.integration.koin)
syncForgeModule { androidContext(this@App); syncManager { /* DSL */ } }

implementation(syncforge.integration.hilt)
SyncForgeHilt.createSyncManager(context) { /* DSL */ }
```

---

## Inject shared HttpClient

```kotlin
SyncForge.android(this) {
    httpClient(buildSyncForgeHttpClient(OkHttp.create(), auth = authProvider, json = KtorSyncTransport.defaultJson))
    auth(authProvider)
    registry(handlers)
}
```

[Recipes → httpClient](RECIPES.md#inject-app-owned-ktor-httpclient).

---

## Hierarchical relationships

```kotlin
@SyncForgeEntity(entityType = "notes")
data class NoteEntity(
    override val id: String,
    val title: String,
    val tagId: String?,
    // ...
) : SyncedEntity
```

[Hierarchical sync](HIERARCHICAL_SYNC.md).

---

## Version catalog

```kotlin
dependencyResolutionManagement {
    versionCatalogs {
        create("syncforge") {
            from("studio.syncforge:syncforge-catalog:2.0.0")
        }
    }
}

dependencies {
    implementation(syncforge.core)
    implementation(syncforge.transport.graphql)
    implementation(syncforge.integration.opentelemetry)
}
plugins {
    alias(syncforge.plugins.syncforge.android)
}
```

See [GETTING_STARTED.md](GETTING_STARTED.md) for the full first-integration walkthrough.