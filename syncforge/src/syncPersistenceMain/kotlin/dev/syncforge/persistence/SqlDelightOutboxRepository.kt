package dev.syncforge.persistence

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.syncforge.model.Change
import dev.syncforge.model.OutboxEntry
import dev.syncforge.outbox.OutboxRepository
import dev.syncforge.sync.RetryBackoff
import dev.syncforge.sync.currentTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * SQLDelight-backed [OutboxRepository] — default on all platforms since 0.6.0.
 */
internal class SqlDelightOutboxRepository(
    private val database: SyncForgePersistenceDatabase,
    private val maxRetries: Int = 5,
) : OutboxRepository {

    private val queries get() = database.outboxQueries
    private val dispatcher = Dispatchers.Default

    override fun observePendingCount(): Flow<Int> =
        queries.observeAwaitingCount(maxRetries.toLong())
            .asFlow()
            .mapToOne(dispatcher)
            .map { it.toInt() }

    override fun observePending(): Flow<List<OutboxEntry>> =
        queries.observeAwaiting(maxRetries.toLong())
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map { it.toModel() } }

    override fun observeAll(): Flow<List<OutboxEntry>> =
        queries.observeAll()
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map { it.toModel() } }

    override suspend fun countPending(): Int = countAwaitingPush()

    override suspend fun countAwaitingPush(): Int =
        withContext(dispatcher) {
            queries.countAwaiting(maxRetries.toLong()).executeAsOne().toInt()
        }

    override suspend fun countPermanentlyFailed(maxRetries: Int): Int =
        withContext(dispatcher) {
            queries.countPermanentlyFailed(maxRetries.toLong()).executeAsOne().toInt()
        }

    override suspend fun enqueue(
        change: Change<*>,
        payloadJson: String?,
        rollbackSnapshotJson: String?,
    ): OutboxEntry = withContext(dispatcher) {
        database.transactionWithResult {
            queries.insertOutboxEntry(
                entityType = change.entityType,
                entityId = change.entityId,
                changeType = change.type.name,
                payloadJson = payloadJson,
                rollbackSnapshotJson = rollbackSnapshotJson,
                localVersion = change.localVersion,
                createdAtMillis = change.updatedAtMillis,
            )
            val id = queries.lastInsertRowId().executeAsOne()
            queries.findById(id).executeAsOne().toModel()
        }
    }

    override suspend fun peek(limit: Int, nowMillis: Long): List<OutboxEntry> =
        withContext(dispatcher) {
            queries.peekReady(
                limit = limit.toLong(),
                nowMillis = nowMillis,
                maxRetries = maxRetries.toLong(),
            ).executeAsList().map { it.toModel() }
        }

    override suspend fun markAcknowledged(ids: List<Long>) {
        if (ids.isEmpty()) return
        withContext(dispatcher) {
            queries.deleteByIds(ids)
        }
    }

    override suspend fun findForEntity(entityType: String, entityId: String): List<OutboxEntry> =
        withContext(dispatcher) {
            queries.findByEntity(entityType = entityType, entityId = entityId)
                .executeAsList()
                .map { it.toModel() }
        }

    override suspend fun removeForEntity(entityType: String, entityId: String) {
        withContext(dispatcher) {
            queries.deleteByEntity(entityType = entityType, entityId = entityId)
        }
    }

    override suspend fun markFailed(id: Long, error: String, retryable: Boolean, maxRetries: Int) {
        withContext(dispatcher) {
            val current = queries.findById(id).executeAsOneOrNull() ?: return@withContext
            val newRetryCount = current.retryCount + 1
            val isPermanent = !retryable || newRetryCount >= maxRetries
            queries.updateOutboxEntry(
                id = current.id,
                entityType = current.entityType,
                entityId = current.entityId,
                changeType = current.changeType,
                payloadJson = current.payloadJson,
                rollbackSnapshotJson = current.rollbackSnapshotJson,
                localVersion = current.localVersion,
                createdAtMillis = current.createdAtMillis,
                retryCount = if (!retryable) maxRetries.toLong() else newRetryCount,
                lastError = error,
                nextRetryAtMillis = if (isPermanent) {
                    null
                } else {
                    RetryBackoff.nextRetryAtMillis(newRetryCount.toInt())
                },
            )
        }
    }

    override suspend fun earliestRetryAtMillis(maxRetries: Int): Long? =
        withContext(dispatcher) {
            queries.earliestFutureRetryAtMillis(
                nowMillis = currentTimeMillis(),
                maxRetries = maxRetries.toLong(),
            ).executeAsOneOrNull()?.MIN
        }

    override suspend fun clear() {
        withContext(dispatcher) {
            queries.clearAll()
        }
    }
}