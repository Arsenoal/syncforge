# SyncForge Spring Boot backend starter

Spring Boot 3 reference server for teams that already run on Spring. Implements the same
SyncForge REST contract as `:backend-starter` (Ktor) using shared handlers from
`:syncforge-server`.

| Module | Role |
|--------|------|
| `:syncforge-server` | `SyncStore`, `SyncHandlers`, `JdbcSyncStore`, Ktor `syncRoutes()` |
| `:backend-starter` | Ktor reference app (in-memory store) |
| `:backend-starter-spring` | Spring Boot reference app (this module) |

## Run (in-memory)

```bash
./gradlew :backend-starter-spring:bootRun
```

Listens on port `8080` by default. Override with `PORT`:

```bash
PORT=9090 ./gradlew :backend-starter-spring:bootRun
```

## Run (JDBC / PostgreSQL)

Start Postgres:

```bash
cd backend-starter-spring
docker compose up -d
```

Run with the `jdbc` profile (Flyway applies schema; `JdbcSyncStore` persists entities):

```bash
./gradlew :backend-starter-spring:bootRun --args='--spring.profiles.active=jdbc'
```

Environment overrides:

| Variable | Default |
|----------|---------|
| `DB_HOST` | `localhost` |
| `DB_PORT` | `5432` |
| `DB_NAME` | `syncforge` |
| `DB_USER` | `syncforge` |
| `DB_PASSWORD` | `syncforge` |

## Auth

Same contract as `:backend-starter` — register/login/refresh, then Bearer token on sync routes.

| Endpoint | Purpose |
|----------|---------|
| `POST /auth/register` | `{ "email", "password" }` → tokens |
| `POST /auth/login` | Same shape |
| `POST /auth/refresh` | `{ "refresh_token" }` → new tokens |
| `POST /sync/push`, `GET /sync/pull` | `Authorization: Bearer <access_token>` |

See [AUTH_API.md](../docs/AUTH_API.md).

## Storage

| `syncforge.store.type` | Implementation |
|------------------------|----------------|
| `in-memory` (default) | [InMemorySyncStore](../syncforge-server/src/main/kotlin/dev/syncforge/server/InMemorySyncStore.kt) |
| `jdbc` | [JdbcSyncStore](../syncforge-server/src/main/kotlin/dev/syncforge/server/JdbcSyncStore.kt) + Flyway `V1__sync_schema.sql` |

Swap in your own `SyncStore` bean to integrate with an existing database layer.

## Contract

See [REST_API.md](../docs/REST_API.md). Stable endpoints:

- `POST /sync/push`
- `GET /sync/pull` — `since`, optional `types` (empty = no filter), `limit`, `cursor`

`GET /health` is included for ops convenience but is not part of the stable 1.0 contract.

## Client base URLs

| Environment | Base URL |
|-------------|----------|
| Android emulator | `http://10.0.2.2:8080` |
| iOS Simulator | `http://127.0.0.1:8080` |
| Desktop / same host | `http://localhost:8080` |

## Next steps

1. Copy `backend-starter-spring/` into your Spring Boot repo or add as a submodule.
2. Replace `InMemoryAuthStore` with your identity provider.
3. Keep `SyncHandlers` + `SyncStore` wiring, or customize controllers while preserving DTO shapes.
4. Run client E2E tests against your server before upgrading SyncForge major versions.