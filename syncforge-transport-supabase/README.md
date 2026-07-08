# SyncForge Supabase transport

`SyncDeltaStore` implementation for [Supabase](https://supabase.com) Postgres via PostgREST RPC.

| Artifact | Role |
|----------|------|
| `:syncforge-transport-core` | `SyncDeltaStore` + `DeltaStoreSyncTransport` |
| `:syncforge-transport-supabase` | `SupabaseSyncDeltaStore` (this module) |

## Setup

1. Apply the SQL migration to your Supabase project:

```bash
# supabase/migrations/001_syncforge_sync_schema.sql
```

Creates `sync_entity`, version counter, `syncforge_push` / `syncforge_pull` RPCs, and adds `sync_entity` to Realtime.

2. Wire the transport in your app:

```kotlin
@OptIn(ExperimentalSyncForgeApi::class)
val config = SupabaseSyncConfig(
    projectUrl = "https://YOUR_PROJECT.supabase.co",
    apiKey = BuildConfig.SUPABASE_ANON_KEY,
    accessToken = { userSession.accessToken }, // optional — for RLS
)

SyncForge.android(this) {
    transport(DeltaStoreSyncTransport(SupabaseSyncDeltaStore(config)))
    registry(SyncForgeHandlers.registry(taskDao))
}
```

## Realtime

After migration, subscribe to `postgres_changes` on `sync_entity` and call `syncManager.sync()` when rows change. See `SupabaseRealtimePatterns` in source.

## RPC contract

Push/pull semantics match `:syncforge-server` `SyncStore` and [REST_API.md](../docs/REST_API.md). Conflict detection runs in Postgres (`SECURITY DEFINER` RPCs).

## Auth

- **Development:** anon or service-role key as `apiKey`
- **Production with RLS:** pass user JWT via `accessToken`; keep `apiKey` as anon key for PostgREST headers