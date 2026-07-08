# SyncForge GraphQL sync contract

Reference schema for the SyncForge delta-sync wire format over GraphQL. Same semantics as
[REST_API.md](../../docs/REST_API.md) — two operations only (`syncPush` / `syncPull`), opaque
`payloadJson` per entity.

| Artifact | Role |
|----------|------|
| `syncforge-sync.graphql` | Canonical SDL (this folder) |
| `:syncforge-server` `graphqlRoutes` | Ktor GraphQL-over-HTTP facade → `SyncHandlers` |
| `:syncforge-transport-graphql` | Client `GraphQlSyncTransport` |
| `:backend-starter-graphql` | Runnable Ktor app exposing `POST /graphql` |

## Wire format

GraphQL-over-HTTP JSON (POST):

```json
{
  "query": "mutation syncPush($entries: [OutboxEntryInput!]!) { ... }",
  "operationName": "syncPush",
  "variables": { "entries": [ ... ] }
}
```

Responses follow the standard `{ "data": { ... } }` / `{ "errors": [ ... ] }` envelope.

## Ktor (recommended for self-hosted)

`:syncforge-server` ships a minimal facade — no GraphQL engine dependency. Mount it on your
`SyncStore` (same store as REST):

```kotlin
import dev.syncforge.server.graphqlRoutes
import dev.syncforge.server.syncRoutes

routing {
    syncRoutes(store)       // optional REST alongside GraphQL
    graphqlRoutes(store)    // POST /graphql
}
```

Resolvers delegate to `SyncHandlers.push` / `SyncHandlers.pull` — identical conflict, pagination,
and tombstone rules as REST.

Runnable examples:

```bash
./gradlew :backend-starter-graphql:run   # GraphQL only
./gradlew :mock-server:run               # REST + GraphQL + /dev/* demos
```

## Apollo Server (Node) sketch

Map mutations/queries to the same store logic as REST. Pseudocode:

```javascript
const resolvers = {
  Mutation: {
    syncPush: async (_, { entries }, { store }) => {
      const response = await store.push(entries.map(toDto));
      return {
        acknowledgedIds: response.acknowledgedIds,
        rejected: response.rejected,
      };
    },
  },
  Query: {
    syncPull: async (_, { since, types, limit, cursor }, { store }) => {
      return store.pull({
        sinceTimestampMillis: since,
        entityTypes: new Set(types),
        pageSize: limit ?? Number.MAX_SAFE_INTEGER,
        pageCursor: cursor,
      });
    },
  },
};
```

Import field names from `syncforge-sync.graphql`. Reuse your REST `SyncStore` implementation
behind the resolver context.

## Spring GraphQL sketch

```kotlin
@Controller
class SyncGraphQlController(private val store: SyncStore) {

    @MutationMapping
    fun syncPush(@Argument entries: List<OutboxEntryDto>): PushResponse =
        SyncHandlers.push(store, PushRequest(entries))

    @QueryMapping
    fun syncPull(
        @Argument since: Long,
        @Argument types: List<String>,
        @Argument limit: Int?,
        @Argument cursor: String?,
    ): PullResponse = SyncHandlers.pull(
        store,
        PullQueryParams(since = since, types = types.toSet(), limit = limit ?: Int.MAX_VALUE, cursor = cursor),
    )
}
```

Add `syncforge-sync.graphql` to your schema locations. Spring Boot starter with JDBC store:
`:backend-starter-spring` (REST today; copy the controller pattern above for GraphQL).

## Client pairing

```kotlin
GraphQlSyncTransport(
    config = GraphQlSyncConfig(endpointUrl = "http://10.0.2.2:8080/graphql"),
    httpClient = appHttpClient,
)
```

See [RECIPES.md](../../docs/RECIPES.md#graphql-sync-transport-client) and
[syncforge-transport-graphql/README.md](../../syncforge-transport-graphql/README.md).

## Contract checklist

1. Expose exactly `syncPush` and `syncPull` with types from `syncforge-sync.graphql`.
2. Keep `payloadJson` opaque — entity schemas are app-owned, not SyncForge-generated.
3. Honour pagination (`hasMore`, `nextPageCursor`) and tombstones (`isDeleted`) like REST.
4. Run `SyncPushPullContract` / client contract tests before upgrading SyncForge major versions.