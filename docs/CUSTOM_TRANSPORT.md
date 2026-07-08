# Custom transport guide

How to connect SyncForge to **your** backend when the shipped adapters (REST, GraphQL,
Supabase, Firebase) are not enough. The sync engine, outbox, conflict resolution, and entity
handlers stay unchanged — you only swap how `push()` and `pull()` reach remote storage.

Assumes [Getting Started](GETTING_STARTED.md) is complete.

---

## What SyncManager needs

`SyncManager` calls a single stable interface — [SyncTransport](../syncforge/src/commonMain/kotlin/dev/syncforge/network/SyncTransport.kt):

```kotlin
interface SyncTransport {
    suspend fun push(entries: List<OutboxEntry>): PushResult
    suspend fun pull(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        pageSize: Int = Int.MAX_VALUE,
        pageCursor: String? = null,
    ): PullResult
}
```

Everything else (Room/SQLDelight, KSP handlers, `conflicts { }`, WorkManager) is independent of
wire format. `payloadJson` on each outbox entry remains **your** entity JSON — SyncForge does
not generate GraphQL types or OpenAPI from `@SyncForgeEntity`.

---

## Choose your approach

```
                    ┌─────────────────────────┐
                    │      SyncManager        │
                    └───────────┬─────────────┘
                                │ SyncTransport
          ┌─────────────────────┼─────────────────────┐
          │                     │                     │
   KtorSyncTransport    GraphQlSyncTransport   DeltaStoreSyncTransport
   (default REST)      (:transport-graphql)     (:transport-core)
          │                     │                     │
   POST /sync/push         syncPush mutation      SyncDeltaStore
   GET  /sync/pull         syncPull query         (your BaaS impl)
```

| Your backend | Recommended path | Artifact |
|--------------|------------------|----------|
| Self-hosted HTTP REST | Default — `baseUrl()` + optional `httpClient()` | `:syncforge-network-ktor` (auto on Android) |
| Self-hosted GraphQL | `GraphQlSyncTransport` | `:syncforge-transport-graphql` |
| Supabase Postgres | `DeltaStoreSyncTransport(SupabaseSyncDeltaStore(...))` | `:syncforge-transport-supabase` |
| Firebase Firestore | `DeltaStoreSyncTransport(FirebaseSyncDeltaStore(...))` | `:syncforge-transport-firebase` |
| Other BaaS / RPC / NoSQL | Implement `SyncDeltaStore` (~100–200 lines) | `:syncforge-transport-core` |
| Proprietary wire (gRPC, WebSocket, SDK) | Implement `SyncTransport` directly | `:syncforge` only |

**Rule of thumb:** if your client already has a **storage SDK** (Firestore, PostgREST, custom
delta table), implement `SyncDeltaStore` and use `DeltaStoreSyncTransport`. If you own a
**custom protocol**, implement `SyncTransport`.

Semantic contract for push/pull (regardless of wire format): [REST_API.md](REST_API.md) and
[Transport adapters](REST_API.md#transport-adapters-client).

---

## Wire on any platform

```kotlin
SyncForge.android(context) {
    transport(myTransport)   // or DeltaStoreSyncTransport(store)
    registry(handlers)
    // baseUrl() optional when transport owns endpoints
}
```

Same `transport { }` on `SyncForge.ios { }`, `SyncForge.desktop { }`, and `SyncForge.macos { }`.

For tests without Android context:

```kotlin
SyncForge.builder {
    handler(taskHandler)
    transport = myTransport
    outbox = InMemoryOutboxRepository()
    scope = testScope
}.build()
```

---

## BYO `SyncTransport`

Use when you map outbox entries and pull cursors to a **custom wire format** (gRPC, domain
GraphQL, vendor SDK, message queue, etc.).

### 1. Map push

```kotlin
class MySyncTransport(
    private val client: MyBackendClient,
) : SyncTransport {

    override suspend fun push(entries: List<OutboxEntry>): PushResult {
        val response = client.uploadBatch(entries.map { it.toDto() })
        return response.toPushResult()
    }
```

When your backend shares REST DTO shapes, reuse helpers from `dev.syncforge.network.api`:

- `OutboxEntry.toDto()` → `OutboxEntryDto`
- `PushResponse.toPushResult()` → `PushResult`
- `PullResponse.toPullResult()` → `PullResult`

### 2. Map pull

```kotlin
    override suspend fun pull(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        pageSize: Int,
        pageCursor: String?,
    ): PullResult {
        val response = client.fetchDeltas(
            since = sinceTimestampMillis,
            types = entityTypes,
            limit = pageSize,
            cursor = pageCursor,
        )
        return response.toPullResult()
    }
}
```

### 3. Honour pagination

`SyncManager` passes `pageSize` from `SyncConfig.pullPageSize`. Return:

| Field | Requirement |
|-------|-------------|
| `deltas` | Changes with `updatedAtMillis > since` (per REST semantics) |
| `serverTimestampMillis` | Server clock for cursor advancement |
| `hasMore` | `true` when more pages exist |
| `nextPageCursor` | Opaque cursor for the next `pull()` call |

Use `pageSize = Int.MAX_VALUE` when your backend returns all deltas in one response.

### 4. Surface errors

Throw [SyncTransportException](../syncforge/src/commonMain/kotlin/dev/syncforge/network/SyncTransportException.kt)
with an appropriate [SyncError.Code](../syncforge/src/commonMain/kotlin/dev/syncforge/model/SyncError.kt):

```kotlin
throw SyncTransportException(
    SyncError(
        code = SyncError.Code.AUTH,
        message = "Token expired",
        httpStatus = 401,
    ),
)
```

| Situation | `SyncError.Code` |
|-----------|------------------|
| Network / timeout / parse failure | `NETWORK` |
| 401 / 403 / invalid token | `AUTH` |
| Stale version / optimistic lock | `CONFLICT` |
| Bad payload / schema | `VALIDATION` |
| 5xx / vendor outage | `SERVER` |

Per-entry rejections on push belong in `PushResult.rejected` (not exceptions) — see
[REST API → push rejections](REST_API.md#post-syncpush).

### 5. Auth

REST transports use `SyncAuthProvider` + `RefreshingSyncAuthProvider` on the DSL. Custom
transports attach tokens inside your client:

```kotlin
SyncForge.android(this) {
    transport(MySyncTransport(client.withBearer { tokenStore.accessToken }))
    registry(handlers)
}
```

Built-in `auth { register/login }` still uses `baseUrl()` for `/auth/*` — independent of sync
transport endpoints.

### 6. Test without a backend

```kotlin
class FakeTransport : SyncTransport {
    override suspend fun push(entries: List<OutboxEntry>) =
        PushResult(acknowledgedIds = entries.map { it.id })

    override suspend fun pull(...) =
        PullResult(deltas = emptyList(), serverTimestampMillis = sinceTimestampMillis)
}

SyncForge.builder {
    transport = FakeTransport()
    // ...
}
```

See [Recipes → Test without a backend](RECIPES.md#test-without-a-backend).

Copy-paste skeleton: [Recipes → Custom SyncTransport](RECIPES.md#custom-synctransport).

---

## BYO `SyncDeltaStore`

Use when the client talks to a **hosted store** (Firestore, Supabase, custom RPC, JDBC bridge)
via an SDK rather than raw HTTP. Implement storage once; `DeltaStoreSyncTransport` implements
`SyncTransport` for you.

### Port

```kotlin
interface SyncDeltaStore {
    suspend fun appendEntries(entries: List<OutboxEntry>): PushResult
    suspend fun queryDeltas(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        pageSize: Int,
        pageCursor: String?,
    ): PullResult
}
```

[`DeltaStoreSyncTransport`](../syncforge-transport-core/src/commonMain/kotlin/dev/syncforge/transport/DeltaStoreSyncTransport.kt)
wraps any implementation and maps exceptions to `SyncTransportException`.

### Wiring

```kotlin
@OptIn(ExperimentalSyncForgeApi::class)
SyncForge.android(this) {
    transport(DeltaStoreSyncTransport(MyRpcSyncDeltaStore(api)))
    registry(handlers)
}
```

### Implementation checklist

1. **Push** — persist each outbox entry as a delta row; return acknowledged outbox ids and
   per-entry rejections (conflict, validation).
2. **Pull** — return deltas with `updatedAtMillis > since`; honour `entityTypes` filter (empty
   set = all types); support `pageCursor` / `hasMore` when `pageSize` is limited.
3. **Tombstones** — `DELETE` pushes and `isDeleted: true` on pull deltas.
4. **Versions** — monotonic `serverVersion` per entity (matches server `SyncStore` semantics).
5. **Auth** — vendor SDK credentials or tokens inside the store impl, not in `DeltaStoreSyncTransport`.

### Contract tests

Run shared scenarios from `dev.syncforge.transport.contract.SyncDeltaStoreContract` in your
module's `commonTest` (see Supabase/Firebase `*ContractTest.kt` and
[transport-core README](../syncforge-transport-core/README.md)).

Copy-paste example: [Recipes → BYO SyncDeltaStore](RECIPES.md#byo-syncdeltastore-baas--hosted-backend).

---

## Shipped reference implementations

| Module | Type | Docs |
|--------|------|------|
| `:syncforge-network-ktor` | REST `KtorSyncTransport` | [REST_API.md](REST_API.md), [Recipes → HttpClient](RECIPES.md#inject-app-owned-ktor-httpclient) |
| `:syncforge-transport-graphql` | GraphQL `GraphQlSyncTransport` | [syncforge-transport-graphql/README.md](../syncforge-transport-graphql/README.md) |
| `:syncforge-transport-supabase` | `SupabaseSyncDeltaStore` | [syncforge-transport-supabase/README.md](../syncforge-transport-supabase/README.md) |
| `:syncforge-transport-firebase` | `FirebaseSyncDeltaStore` | [syncforge-transport-firebase/README.md](../syncforge-transport-firebase/README.md) |
| `:syncforge-transport-core` | `DeltaStoreSyncTransport` + contract kit | [syncforge-transport-core/README.md](../syncforge-transport-core/README.md) |

Server-side mirror of the same semantics: `SyncStore` in `:syncforge-server` (REST and GraphQL
routes).

---

## What does not change

| Unchanged | Notes |
|-----------|-------|
| `@SyncForgeEntity` / KSP handlers | Codegen is transport-agnostic |
| `registry(SyncForgeHandlers.registry(...))` | Entity apply logic on pull |
| `conflicts { }` policies | Run after pull, before Room write |
| Outbox + conflict DB | SQLDelight persistence |
| `payloadJson` schema | Your domain — version per `entityType` |

---

## Related docs

| Topic | Link |
|-------|------|
| Push/pull JSON contract (semantic source of truth) | [REST_API.md](REST_API.md) |
| GraphQL wire format | [syncforge-server/graphql/README.md](../syncforge-server/graphql/README.md) |
| Copy-paste recipes | [RECIPES.md](RECIPES.md) |
| Package reference | [MODULES.md](MODULES.md) |
| Bearer auth + 401 refresh | [AUTH_API.md](AUTH_API.md), [Recipes → Token refresh](RECIPES.md#token-refresh-on-401) |