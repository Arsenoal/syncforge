# SyncForge REST API contract

SyncForge defines a minimal delta-sync HTTP contract. The reference implementations are:

- **Client:** `KtorSyncTransport` (`:syncforge` androidMain)
- **Server (production starter):** `:backend-starter` (Ktor) or `:backend-starter-spring` (Spring Boot) + `:syncforge-server` library
- **Server (local dev / demos):** `:mock-server` (adds `/dev/*` routes for conflict demos)

DTOs live in `dev.syncforge.network.api` (`commonMain`) and are shared between client
and server.

---

## API versioning and compatibility

### Contract version

The HTTP contract is versioned **with the SyncForge library** using [Semantic Versioning](https://semver.org/):

| Library version | Contract status |
|-----------------|-----------------|
| `0.x.y` | Evolving — endpoints and JSON fields may change in minor releases; breaking changes are documented in [CHANGELOG.md](../CHANGELOG.md) |
| `1.0.0+` | Stable — breaking HTTP/JSON changes only in **major** library releases |

There is **no separate URL prefix** (e.g. `/v1/sync/push`) in 1.0. Paths stay `/sync/push` and
`/sync/pull`. Backends implement the contract that matches the SyncForge client version they
target.

### What is part of the stable contract (1.0+)

| In scope | Notes |
|----------|-------|
| `POST /sync/push` | Request/response JSON shape in this document |
| `GET /sync/pull` | Query params and response JSON shape in this document |
| `PushRejection.code` values | `NETWORK`, `AUTH`, `CONFLICT`, `VALIDATION`, `SERVER` (+ unknown → client `UNKNOWN`) |
| Pagination fields | `hasMore`, `nextPageCursor`, `limit`, `cursor` — optional on server; clients handle absence |
| `Authorization: Bearer` | Optional; `401` / `403` semantics as documented |

### Out of scope (not guaranteed stable)

| Item | Notes |
|------|-------|
| `GET /health` | Mock/dev convenience only |
| `POST /dev/simulate-edit` | Mock/dev only — never implement in production |
| Entity `payloadJson` schemas | **Your** domain — version per `entityType` in your API, not SyncForge semver |
| Undocumented endpoints or fields | Clients ignore unknown JSON fields today; do not rely on that for required server behaviour |

### Change categories

**Patch (0.x patch / 1.x patch)** — no contract migration required:

- Documentation clarifications
- Mock-server bug fixes with no wire-format change
- Server-side performance or idempotency improvements

**Minor (0.x minor / 1.x minor)** — backward compatible for conforming servers and SyncForge clients:

- New **optional** JSON fields on requests or responses (clients and servers must ignore unknown fields)
- New **optional** query parameters on `GET /sync/pull`
- New rejection `code` values (map to client `SyncError.Code.UNKNOWN` until library support is added)

**Major (1.x → 2.x)** — breaking contract change; requires coordinated upgrade:

- Removing or renaming endpoints
- Changing required JSON fields or types
- Changing meaning of existing fields (e.g. cursor semantics)
- Making optional pagination fields required without a compatibility period

Breaking changes are listed under **Removed** or **Changed** in `CHANGELOG.md` and require a
major SyncForge release. Migration notes for backend authors are published in the same release.

### Backend implementation checklist

1. Implement `POST /sync/push` and `GET /sync/pull` as specified below.
2. Return `200` with the documented JSON bodies on success; use `4xx`/`5xx` with a body only when
   your framework requires it — SyncForge maps transport failures to `SyncError`, not response bodies.
3. Treat push batches as **idempotent** where possible (safe client retries).
4. Use monotonic `serverVersion` and `updatedAtMillis` per entity; honour tombstones on pull (`isDeleted: true`).
5. Pin your tested contract to a SyncForge release (e.g. `1.1.0`) in your service README or OpenAPI description.

### Testing against a reference server

| Target | How |
|--------|-----|
| Production starter (Ktor) | `./gradlew :backend-starter:run` — see `backend-starter/README.md` |
| Production starter (Spring) | `./gradlew :backend-starter-spring:bootRun` — JDBC via `jdbc` profile; see `backend-starter-spring/README.md` |
| Local dev + conflict demos | `./gradlew :mock-server:run` — contract + `/dev/simulate-edit` |
| CI / samples | `:mock-server` on port `8080`; Android emulator uses `http://10.0.2.2:8080` |
| Contract drift | Run client E2E (`./gradlew androidE2e`, `./gradlew iosE2e`) against your backend before upgrading SyncForge major versions |

### Future negotiation (post-1.0, if needed)

If a `/v2` path or header-based negotiation is introduced later, it will be announced in
`CHANGELOG.md` with a deprecation window for the 1.x wire format. **1.0 clients do not send
a contract version header.**

---

## Base URL

All paths are relative to the configured base URL, e.g. `https://api.example.com` or
`http://10.0.2.2:8080` (Android emulator → host machine).

---

## Authentication (optional)

Clients may send:

```
Authorization: Bearer <token>
```

`KtorSyncTransport` supports this via `authTokenProvider: () -> String?` or
`RefreshingSyncAuthProvider` for automatic refresh on expired tokens. The mock server
ignores auth. Production backends should:

| Status | Meaning | Client behavior |
|--------|---------|-----------------|
| `401` | Missing/invalid/expired token | `SyncError.Code.AUTH`; with `RefreshingSyncAuthProvider`, refresh once and retry |
| `403` | Valid auth but forbidden | `SyncError.Code.AUTH`; no automatic retry |

---

## `POST /sync/push`

Push a batch of outbox entries to the server. Entries should be processed idempotently
where possible — clients may retry the same batch after network failures.

### Request body

```json
{
  "entries": [
    {
      "id": 42,
      "entityType": "tasks",
      "entityId": "550e8400-e29b-41d4-a716-446655440000",
      "changeType": "CREATE",
      "payloadJson": "{\"id\":\"550e8400-...\",\"title\":\"Buy milk\",\"completed\":false}",
      "localVersion": 1,
      "createdAtMillis": 1719667200000
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `id` | `Long` | Client-side outbox row ID (used for acknowledgement) |
| `entityType` | `String` | Entity type key, e.g. `"tasks"` |
| `entityId` | `String` | Primary key of the affected entity |
| `changeType` | `"CREATE"` \| `"UPDATE"` \| `"DELETE"` | Mutation kind |
| `payloadJson` | `String?` | JSON entity payload; `null` for `DELETE` |
| `localVersion` | `Long` | Client-side version counter |
| `createdAtMillis` | `Long` | When the outbox entry was created |

### Response `200 OK`

```json
{
  "acknowledgedIds": [42],
  "rejected": []
}
```

| Field | Type | Description |
|-------|------|-------------|
| `acknowledgedIds` | `Long[]` | Outbox IDs the server accepted |
| `rejected` | `PushRejection[]` | Entries the server refused |

**Rejection entry:**

```json
{
  "outboxId": 42,
  "code": "VALIDATION",
  "message": "title must not be empty"
}
```

| `code` value | Maps to `SyncError.Code` |
|--------------|--------------------------|
| `NETWORK` | `NETWORK` |
| `AUTH` | `AUTH` |
| `CONFLICT` | `CONFLICT` |
| `VALIDATION` | `VALIDATION` |
| `SERVER` | `SERVER` |
| anything else | `UNKNOWN` |

### Server behaviour

- **CREATE / UPDATE** — upsert the entity; assign a monotonic `serverVersion` and `updatedAtMillis`
- **DELETE** — store a tombstone (`isDeleted: true`) so pull clients can propagate the delete
- Rejected entries are rolled back locally by SyncForge; acknowledged entries are removed from the outbox

---

## `GET /sync/pull`

Fetch server-side changes since a cursor timestamp.

### Query parameters

| Parameter | Required | Description |
|-----------|----------|-------------|
| `since` | No | Epoch millis cursor; `0` means full sync. Default: `0` |
| `types` | No | Comma-separated entity types, e.g. `tasks,notes`. Empty = no filter |
| `limit` | No | Max deltas per response. Maps to `SyncConfig.pullPageSize` on the client. Omit for a single unpaginated response |
| `cursor` | No | Opaque page cursor from a previous response's `nextPageCursor` |

Example:

```
GET /sync/pull?since=1719667200000&types=tasks&limit=100
GET /sync/pull?since=1719667200000&types=tasks&limit=100&cursor=abc123
```

### Response `200 OK`

```json
{
  "deltas": [
    {
      "entityType": "tasks",
      "entityId": "550e8400-e29b-41d4-a716-446655440000",
      "payloadJson": "{\"id\":\"550e8400-...\",\"title\":\"Buy milk\",\"completed\":false}",
      "serverVersion": 3,
      "updatedAtMillis": 1719667300000,
      "isDeleted": false
    }
  ],
  "serverTimestampMillis": 1719667400000,
  "hasMore": false,
  "nextPageCursor": null
}
```

| Field | Type | Description |
|-------|------|-------------|
| `deltas` | `RemoteDelta[]` | Changes since `since` for the requested types |
| `serverTimestampMillis` | `Long` | New cursor — client stores this as the next `since` value |
| `hasMore` | `Boolean` | `true` when more pages remain; client loops until `false` |
| `nextPageCursor` | `String?` | Opaque cursor for the next page; pass as `cursor` query param |

**Delta entry:**

| Field | Type | Description |
|-------|------|-------------|
| `entityType` | `String` | Entity type key |
| `entityId` | `String` | Entity primary key |
| `payloadJson` | `String?` | JSON payload; `null` for tombstones |
| `serverVersion` | `Long` | Server-assigned version |
| `updatedAtMillis` | `Long` | Server timestamp of this change |
| `isDeleted` | `Boolean` | `true` = tombstone; delete locally |

### Client behaviour

- SyncForge applies each delta via the matching `EntitySyncHandler`
- Conflicts are resolved using the configured `ConflictPolicy` (default: last-write-wins auto-resolve)
- Deferred conflicts (`deferToUser()`) are persisted in SyncForge's conflict store and surfaced via `SyncManager.conflicts`
- When `hasMore` is `true`, the client requests subsequent pages using `nextPageCursor` before advancing the pull cursor
- On success, `serverTimestampMillis` is persisted via `SyncCursorStore`

---

## `GET /health` (mock server only)

Optional health check used by the mock server:

```json
{ "status": "ok" }
```

Not required for production backends.

---

## `POST /dev/simulate-edit` (mock server only)

Dev-only endpoint for conflict demos in the sample app. Simulates a concurrent server edit
so the next pull produces a conflict against local changes.

The entity must already exist on the server (push first).

### Request body

```json
{
  "entityType": "tasks",
  "entityId": "550e8400-e29b-41d4-a716-446655440000",
  "payloadJson": "{\"id\":\"550e8400-...\",\"title\":\"Edited on server\",\"completed\":true}"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `entityType` | `String` | Entity type key |
| `entityId` | `String` | Primary key of the entity to update |
| `payloadJson` | `String` | Full replacement JSON payload |

### Response `200 OK`

```json
{ "updated": true }
```

### Response `404 Not Found`

Returned when the entity does not exist on the server yet:

```json
{
  "updated": false,
  "message": "Entity not found — sync the task to the server first"
}
```

Not required for production backends.

---

## Pagination compatibility

Servers that omit `limit` / `cursor` remain compatible — a single response with
`hasMore: false` is treated as one page. Clients using `SyncConfig.pullPageSize` loop
until all pages are consumed before advancing the pull cursor.

---

## Kotlin DTO reference

| DTO | Package |
|-----|---------|
| `PushRequest` | `dev.syncforge.network.api` |
| `OutboxEntryDto` | `dev.syncforge.network.api` |
| `PushResponse` | `dev.syncforge.network.api` |
| `PushRejectionDto` | `dev.syncforge.network.api` |
| `PullResponse` | `dev.syncforge.network.api` |
| `RemoteDeltaDto` | `dev.syncforge.network.api` |

Mapping helpers: `OutboxEntry.toDto()`, `PushResponse.toPushResult()`, `PullResponse.toPullResult()`.