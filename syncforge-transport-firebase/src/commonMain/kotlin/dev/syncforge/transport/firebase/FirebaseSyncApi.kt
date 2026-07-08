package dev.syncforge.transport.firebase

import dev.syncforge.model.OutboxEntry
import dev.syncforge.network.api.PullResponse
import dev.syncforge.network.api.PushResponse

/** Low-level HTTPS boundary for Firebase Cloud Functions sync storage. */
interface FirebaseSyncApi {
    suspend fun push(entries: List<OutboxEntry>, nowMillis: Long): PushResponse

    suspend fun pull(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        pageSize: Int,
        pageCursor: String?,
        nowMillis: Long,
    ): PullResponse
}