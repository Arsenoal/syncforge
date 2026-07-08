package dev.syncforge.transport.supabase

import dev.syncforge.model.OutboxEntry
import dev.syncforge.network.api.PullResponse
import dev.syncforge.network.api.PushResponse

/** Low-level PostgREST RPC boundary for Supabase sync storage. */
interface SupabaseSyncApi {
    suspend fun push(entries: List<OutboxEntry>, nowMillis: Long): PushResponse

    suspend fun pull(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        pageSize: Int,
        pageCursor: String?,
        nowMillis: Long,
    ): PullResponse
}