package dev.syncforge.outbox

import dev.syncforge.model.Change
import dev.syncforge.model.OutboxEntry
import kotlinx.coroutines.flow.Flow

/**
 * Outbox persistence — Android implementation uses Room; common contract stays KMP-ready.
 */
interface OutboxRepository {

    /** Observe pending entry count for [dev.syncforge.model.SyncStatus.Pending]. */
    fun observePendingCount(): Flow<Int>

    fun observePending(): Flow<List<OutboxEntry>>

    /** All outbox rows for debug inspection (includes permanently failed). */
    fun observeAll(): Flow<List<OutboxEntry>>

    suspend fun enqueue(
        change: Change<*>,
        payloadJson: String? = null,
        rollbackSnapshotJson: String? = null,
    ): OutboxEntry

    suspend fun countPending(): Int

    /** Entries not permanently failed (awaiting push or retry). */
    suspend fun countAwaitingPush(): Int

    /** Entries that exceeded [maxRetries]. */
    suspend fun countPermanentlyFailed(maxRetries: Int): Int

    suspend fun peek(limit: Int = 50, nowMillis: Long): List<OutboxEntry>

    suspend fun markAcknowledged(ids: List<Long>)

    suspend fun markFailed(id: Long, error: String, retryable: Boolean = true, maxRetries: Int)

    /** Earliest [OutboxEntry.nextRetryAtMillis] among entries waiting for backoff. */
    suspend fun earliestRetryAtMillis(maxRetries: Int): Long?

    suspend fun clear()
}