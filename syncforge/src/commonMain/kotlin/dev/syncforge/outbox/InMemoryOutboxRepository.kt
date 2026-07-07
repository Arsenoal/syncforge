package dev.syncforge.outbox

import dev.syncforge.model.Change
import dev.syncforge.model.OutboxEntry
import dev.syncforge.sync.RetryBackoff
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory outbox for unit tests and early integration prototyping.
 */
class InMemoryOutboxRepository : OutboxRepository {

    private val mutex = Mutex()
    private val entries = mutableListOf<OutboxEntry>()
    private val version = MutableStateFlow(0)
    private var maxRetriesConfig: Int = 5

    /** Override for tests that need deterministic retry timing (shared with [SyncEngine] clock). */
    var clock: () -> Long = { dev.syncforge.sync.currentTimeMillis() }

    fun setMaxRetriesForObservation(maxRetries: Int) {
        maxRetriesConfig = maxRetries
    }

    override fun observePendingCount(): Flow<Int> =
        version.map { entries.countAwaitingPush(maxRetriesConfig) }

    override fun observePending(): Flow<List<OutboxEntry>> =
        version.map { entries.filter { !it.isPermanentlyFailed(maxRetriesConfig) } }

    override fun observeAll(): Flow<List<OutboxEntry>> =
        version.map { entries.sortedByDescending { it.createdAtMillis } }

    override suspend fun countPending(): Int = countAwaitingPush()

    override suspend fun countAwaitingPush(): Int = mutex.withLock {
        entries.countAwaitingPush(maxRetriesConfig)
    }

    override suspend fun countPermanentlyFailed(maxRetries: Int): Int = mutex.withLock {
        entries.countPermanentlyFailed(maxRetries)
    }

    override suspend fun enqueue(
        change: Change<*>,
        payloadJson: String?,
        rollbackSnapshotJson: String?,
    ): OutboxEntry = mutex.withLock {
        val entry = OutboxEntry(
            id = (entries.maxOfOrNull { it.id } ?: 0L) + 1,
            entityType = change.entityType,
            entityId = change.entityId,
            changeType = change.type,
            payloadJson = payloadJson,
            rollbackSnapshotJson = rollbackSnapshotJson,
            localVersion = change.localVersion,
            createdAtMillis = change.updatedAtMillis,
        )
        entries += entry
        version.value++
        entry
    }

    override suspend fun peek(limit: Int, nowMillis: Long): List<OutboxEntry> = mutex.withLock {
        entries.readyForPush(nowMillis, maxRetriesConfig, limit)
    }

    override suspend fun markAcknowledged(ids: List<Long>) {
        mutex.withLock {
            entries.removeAll { it.id in ids }
            version.value++
        }
    }

    override suspend fun markFailed(id: Long, error: String, retryable: Boolean, maxRetries: Int) =
        mutex.withLock {
            val index = entries.indexOfFirst { it.id == id }
            if (index < 0) return@withLock
            val current = entries[index]
            val newRetryCount = current.retryCount + 1
            val isPermanent = !retryable || newRetryCount >= maxRetries
            entries[index] = current.copy(
                retryCount = if (!retryable) maxRetries else newRetryCount,
                lastError = error,
                nextRetryAtMillis = if (isPermanent) {
                    null
                } else {
                    RetryBackoff.nextRetryAtMillis(newRetryCount, nowMillis = nowMillis())
                },
            )
            version.value++
        }

    override suspend fun earliestRetryAtMillis(maxRetries: Int): Long? = mutex.withLock {
        entries.earliestRetryAtMillis(maxRetries)
    }

    override suspend fun clear() {
        mutex.withLock {
            entries.clear()
            version.value++
        }
    }

    private fun nowMillis(): Long = clock()
}