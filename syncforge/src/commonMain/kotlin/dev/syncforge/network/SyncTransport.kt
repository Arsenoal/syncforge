package dev.syncforge.network

import dev.syncforge.model.OutboxEntry
import dev.syncforge.model.SyncError
import dev.syncforge.model.SyncResult

/**
 * Pluggable network boundary — default `KtorSyncTransport` (REST), or implement for GraphQL / custom wire formats.
 * REST push/pull HTTP execution is factored behind [SyncHttpClient] (1.1); [SyncTransport] stays the engine boundary.
 *
 * SyncForge never dictates API shape beyond these delta-sync primitives.
 */
interface SyncTransport {

    /**
     * Push a batch of outbox entries to the backend.
     * Implementations should be idempotent where possible.
     */
    suspend fun push(entries: List<OutboxEntry>): PushResult

    /**
     * Pull server changes since [sinceTimestampMillis] (0 = full sync).
     *
     * @param pageSize max deltas per page; use [Int.MAX_VALUE] for no limit
     * @param pageCursor opaque cursor from a previous page's [PullResult.nextPageCursor]
     */
    suspend fun pull(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        pageSize: Int = Int.MAX_VALUE,
        pageCursor: String? = null,
    ): PullResult
}

data class PushResult(
    val acknowledgedIds: List<Long>,
    val rejected: List<PushRejection> = emptyList(),
)

data class PushRejection(
    val outboxId: Long,
    val error: SyncError,
)

data class PullResult(
    val deltas: List<RemoteDelta>,
    val serverTimestampMillis: Long,
    val hasMore: Boolean = false,
    val nextPageCursor: String? = null,
)

/**
 * A single server-side change in a pull response.
 */
data class RemoteDelta(
    val entityType: String,
    val entityId: String,
    val payloadJson: String?,
    val serverVersion: Long,
    val updatedAtMillis: Long,
    val isDeleted: Boolean = false,
)

/**
 * No-op transport for tests and offline-only development.
 */
object NoOpSyncTransport : SyncTransport {
    override suspend fun push(entries: List<OutboxEntry>): PushResult =
        PushResult(acknowledgedIds = entries.map { it.id })

    override suspend fun pull(
        sinceTimestampMillis: Long,
        entityTypes: Set<String>,
        pageSize: Int,
        pageCursor: String?,
    ): PullResult = PullResult(deltas = emptyList(), serverTimestampMillis = sinceTimestampMillis)
}