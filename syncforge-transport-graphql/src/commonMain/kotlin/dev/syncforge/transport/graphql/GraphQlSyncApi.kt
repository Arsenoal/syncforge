package dev.syncforge.transport.graphql

import dev.syncforge.model.OutboxEntry
import dev.syncforge.network.PullResult
import dev.syncforge.network.PushResult

/**
 * Low-level GraphQL push/pull client — maps SyncForge operations to GraphQL-over-HTTP.
 */
interface GraphQlSyncApi {
    suspend fun push(entries: List<OutboxEntry>): PushResult

    suspend fun pull(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        pageSize: Int,
        pageCursor: String?,
    ): PullResult
}