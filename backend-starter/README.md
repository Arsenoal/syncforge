# SyncForge backend starter

Minimal JVM Ktor server that implements the SyncForge REST contract (`POST /sync/push`,
`GET /sync/pull`). Use it as a starting point for your own backend, or run it locally while
building a SyncForge client.

| Module | Role |
|--------|------|
| `:syncforge-server` | Shared routes, DTO wiring, and `SyncStore` contract |
| `:backend-starter` | Runnable Ktor reference app (this module) |
| `:backend-starter-spring` | Spring Boot reference app with optional JDBC store |
| `:backend-starter-graphql` | GraphQL-only reference app (`POST /graphql`) |
| `:mock-server` | Same contract **plus** `/dev/*` routes for conflict demos |

## Run

```bash
./gradlew :backend-starter:run
```

Listens on port `8080` by default. Override with `PORT`:

```bash
PORT=9090 ./gradlew :backend-starter:run
```

Point your SyncForge client `baseUrl` at the server:

| Environment | Base URL |
|-------------|----------|
| Android emulator | `http://10.0.2.2:8080` |
| iOS Simulator | `http://127.0.0.1:8080` |
| Desktop / same host | `http://localhost:8080` |

## Auth (register / login / refresh)

`:backend-starter` includes in-memory auth routes and **requires Bearer tokens** on sync endpoints.

| Endpoint | Purpose |
|----------|---------|
| `POST /auth/register` | `{ "email", "password" }` → tokens |
| `POST /auth/login` | Same shape |
| `POST /auth/refresh` | `{ "refresh_token" }` → new tokens |
| `POST /sync/push`, `GET /sync/pull` | `Authorization: Bearer <access_token>` |

Client setup and Android sequence diagram: [AUTH_API.md](../docs/AUTH_API.md#android-auth-flow).

## Storage

This starter uses [InMemorySyncStore](../syncforge-server/src/main/kotlin/dev/syncforge/server/InMemorySyncStore.kt) —
data is lost on restart. For production, implement [SyncStore](../syncforge-server/src/main/kotlin/dev/syncforge/server/SyncStore.kt)
with your database and swap it in `BackendStarter.kt`:

```kotlin
routing {
    syncRoutes(myDatabaseSyncStore)
}
```

## Contract

See [REST API](../docs/REST_API.md). Stable endpoints:

- `POST /sync/push`
- `GET /sync/pull` — `since`, optional `types` (empty = no filter), `limit`, `cursor`

`GET /health` is included for ops convenience but is not part of the stable 1.0 contract.

## Next steps

1. Copy `backend-starter/` into your own repo or add it as a submodule.
2. Replace `InMemorySyncStore` with persistent storage.
3. Add authentication (Bearer token) if required.
4. Run client E2E tests against your server before upgrading SyncForge major versions.