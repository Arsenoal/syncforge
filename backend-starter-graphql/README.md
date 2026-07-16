# SyncForge GraphQL backend starter

Minimal JVM Ktor server exposing the SyncForge GraphQL sync contract at `POST /graphql`.
Pair with `:syncforge-transport-graphql` on the client.

| Module | Role |
|--------|------|
| `:syncforge-server` | `graphqlRoutes` facade + `SyncHandlers` |
| `:backend-starter-graphql` | Runnable GraphQL reference app (this module) |
| `:backend-starter` | REST + auth reference |
| `:mock-server` | REST + GraphQL + `/dev/*` conflict demos |

## Run

```bash
./gradlew :backend-starter-graphql:run
```

Default port `8080` (override with `PORT`).

## Client

```kotlin

SyncForge.android(this) {
    transport(
        GraphQlSyncTransport(
            config = GraphQlSyncConfig(endpointUrl = "http://10.0.2.2:8080/graphql"),
            httpClient = appHttpClient,
        ),
    )
    registry(SyncForgeHandlers.registry(taskDao))
}
```

## Schema

SDL: [syncforge-server/graphql/syncforge-sync.graphql](../syncforge-server/graphql/syncforge-sync.graphql)

Resolver recipes (Ktor, Apollo Server, Spring): [syncforge-server/graphql/README.md](../syncforge-server/graphql/README.md)

## Storage

Uses `InMemorySyncStore` — swap for your `SyncStore` implementation (same as REST starters).

## Auth

This starter does **not** require Bearer tokens (unlike `:backend-starter`). Add
`installSyncBearerAuth` and wrap `graphqlRoutes` in `authenticate { }` for production.