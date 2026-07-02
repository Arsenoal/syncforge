package dev.syncforge.server

import dev.syncforge.network.api.OutboxEntryDto
import dev.syncforge.network.api.PushResponse
import dev.syncforge.network.api.PullResponse

/**
 * Minimal server-side sync persistence contract for [SyncRoutes].
 *
 * Replace [InMemorySyncStore] with your own database-backed implementation in production.
 */
interface SyncStore {
    fun push(entries: List<OutboxEntryDto>, nowMillis: Long): PushResponse

    fun pull(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        nowMillis: Long,
        limit: Int = Int.MAX_VALUE,
        pageCursor: String? = null,
    ): PullResponse
}