package dev.syncforge.outbox

import dev.syncforge.model.Change
import dev.syncforge.model.OutboxEntry
import dev.syncforge.sync.RetryBackoff
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed persistent outbox — survives process death and reboots.
 */
internal class RoomOutboxRepository(
    private val dao: OutboxDao,
    private val maxRetries: Int = 5,
) : OutboxRepository {

    override fun observePendingCount(): Flow<Int> = dao.observeAwaitingCount(maxRetries)

    override fun observePending(): Flow<List<OutboxEntry>> =
        dao.observeAwaiting(maxRetries).map { entries -> entries.map { it.toModel() } }

    override fun observeAll(): Flow<List<OutboxEntry>> =
        dao.observeAll().map { entries -> entries.map { it.toModel() } }

    override suspend fun countPending(): Int = countAwaitingPush()

    override suspend fun countAwaitingPush(): Int = dao.countAwaiting(maxRetries)

    override suspend fun countPermanentlyFailed(maxRetries: Int): Int =
        dao.countPermanentlyFailed(maxRetries)

    override suspend fun enqueue(
        change: Change<*>,
        payloadJson: String?,
        rollbackSnapshotJson: String?,
    ): OutboxEntry {
        val entity = OutboxEntryEntity(
            entityType = change.entityType,
            entityId = change.entityId,
            changeType = change.type.name,
            payloadJson = payloadJson,
            rollbackSnapshotJson = rollbackSnapshotJson,
            localVersion = change.localVersion,
            createdAtMillis = change.updatedAtMillis,
        )
        val id = dao.insert(entity)
        return entity.copy(id = id).toModel()
    }

    override suspend fun peek(limit: Int, nowMillis: Long): List<OutboxEntry> =
        dao.peekReady(limit, nowMillis, maxRetries).map { it.toModel() }

    override suspend fun markAcknowledged(ids: List<Long>) {
        if (ids.isNotEmpty()) {
            dao.deleteByIds(ids)
        }
    }

    override suspend fun markFailed(id: Long, error: String, retryable: Boolean, maxRetries: Int) {
        val current = dao.findById(id) ?: return
        val newRetryCount = current.retryCount + 1
        val isPermanent = !retryable || newRetryCount >= maxRetries
        dao.update(
            current.copy(
                retryCount = if (!retryable) maxRetries else newRetryCount,
                lastError = error,
                nextRetryAtMillis = if (isPermanent) {
                    null
                } else {
                    RetryBackoff.nextRetryAtMillis(newRetryCount)
                },
            ),
        )
    }

    override suspend fun earliestRetryAtMillis(maxRetries: Int): Long? =
        dao.earliestFutureRetryAtMillis(System.currentTimeMillis(), maxRetries)

    override suspend fun clear() {
        dao.clearAll()
    }
}