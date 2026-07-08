# SyncForge GraphQL transport

Ready-made [GraphQlSyncTransport](src/commonMain/kotlin/dev/syncforge/transport/graphql/GraphQlSyncTransport.kt) — maps SyncForge push/pull to GraphQL-over-HTTP (`syncPush` mutation + `syncPull` query).

| Artifact | Role |
|----------|------|
| `:syncforge-network-ktor` | REST reference transport (`KtorSyncTransport`) |
| `:syncforge-transport-graphql` | GraphQL transport (this module) |

## Setup

Point at a GraphQL endpoint that exposes the SyncForge sync operations (same semantics as [REST_API.md](../docs/REST_API.md)). Schema and resolver recipes: [syncforge-server/graphql/README.md](../syncforge-server/graphql/README.md).

```kotlin
@OptIn(ExperimentalSyncForgeApi::class)
val config = GraphQlSyncConfig(
    endpointUrl = "http://10.0.2.2:8080/graphql",
    bearerToken = { tokenStore.accessToken },
)

SyncForge.android(this) {
    transport(GraphQlSyncTransport(config, appHttpClient))
    registry(SyncForgeHandlers.registry(taskDao))
}
```

Pass a shared Ktor `HttpClient` (same pattern as `KtorSyncTransport`):

```kotlin
GraphQlSyncTransport(
    config = GraphQlSyncConfig(endpointUrl = "https://api.example.com/graphql"),
    httpClient = appHttpClient,
    auth = refreshingAuthProvider,
)
```

## Operations

| REST | GraphQL |
|------|---------|
| `POST /sync/push` | `mutation syncPush(entries: [OutboxEntryInput!]!): PushPayload!` |
| `GET /sync/pull` | `query syncPull(since: Long!, types: [String!]!, limit: Int, cursor: String): PullPayload!` |

`payloadJson` remains an opaque string — entity handlers parse it; GraphQL does not need your Room schema.

## Mock server

```bash
./gradlew :mock-server:run
# GraphQL endpoint: http://localhost:8080/graphql
```