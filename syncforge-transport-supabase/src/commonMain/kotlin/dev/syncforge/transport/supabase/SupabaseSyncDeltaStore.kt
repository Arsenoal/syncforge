package dev.syncforge.transport.supabase

import dev.syncforge.model.OutboxEntry
import dev.syncforge.network.PullResult
import dev.syncforge.network.PushResult
import dev.syncforge.network.api.toPullResult
import dev.syncforge.network.api.toPushResult
import dev.syncforge.transport.SyncDeltaStore
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * [SyncDeltaStore] backed by Supabase Postgres RPC (`syncforge_push` / `syncforge_pull`).
 *
 * Apply `supabase/migrations/001_syncforge_sync_schema.sql` to your project, then wire:
 *
 * ```
 * transport(DeltaStoreSyncTransport(SupabaseSyncDeltaStore(config)))
 * ```
 *
 * For background sync, subscribe to Realtime `postgres_changes` on `sync_entity` — see
 * [SupabaseRealtimePatterns].
 */
class SupabaseSyncDeltaStore(
    private val api: SupabaseSyncApi,
    private val clock: () -> Long = { currentTimeMillis() },
) : SyncDeltaStore {

    constructor(
        config: SupabaseSyncConfig,
        httpClient: HttpClient = defaultHttpClient(),
    ) : this(PostgrestSupabaseSyncApi(config, httpClient))

    override suspend fun appendEntries(entries: List<OutboxEntry>): PushResult =
        api.push(entries, clock()).toPushResult()

    override suspend fun queryDeltas(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        pageSize: Int,
        pageCursor: String?,
    ): PullResult = api.pull(
        sinceTimestampMillis = sinceTimestampMillis,
        entityTypes = entityTypes,
        pageSize = pageSize,
        pageCursor = pageCursor,
        nowMillis = clock(),
    ).toPullResult()

    companion object {
        fun defaultHttpClient(
            json: Json = Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                isLenient = true
            },
        ): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(json)
            }
        }
    }
}

internal expect fun currentTimeMillis(): Long