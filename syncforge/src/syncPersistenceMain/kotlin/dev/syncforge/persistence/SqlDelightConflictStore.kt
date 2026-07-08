package dev.syncforge.persistence

import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.syncforge.conflict.ConflictRecord
import dev.syncforge.conflict.ConflictResolutionKind
import dev.syncforge.conflict.ConflictStatus
import dev.syncforge.conflict.ConflictStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * SQLDelight-backed [ConflictStore] — default on all platforms since 0.6.0.
 */
internal class SqlDelightConflictStore(
    private val database: SyncForgePersistenceDatabase,
) : ConflictStore {

    private val queries get() = database.conflictsQueries
    private val dispatcher = Dispatchers.Default

    override fun observeAll(): Flow<List<ConflictRecord>> =
        queries.observeAll()
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map { it.toRecord() } }

    override fun observeOpen(): Flow<List<ConflictRecord>> =
        queries.observeOpen(openStatus = ConflictStatus.OPEN.name)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map { it.toRecord() } }

    override suspend fun countOpen(): Int =
        withContext(dispatcher) {
            queries.countOpen(openStatus = ConflictStatus.OPEN.name).awaitAsOne().toInt()
        }

    override suspend fun recordDeferred(
        entityType: String,
        entityId: String,
        localJson: String,
        remoteJson: String?,
        localUpdatedAtMillis: Long,
        remoteServerVersion: Long,
        remoteUpdatedAtMillis: Long,
        detectedAtMillis: Long,
    ): Long = withContext(dispatcher) {
        database.transactionWithResult {
            queries.insertConflict(
                entityType = entityType,
                entityId = entityId,
                localJson = localJson,
                remoteJson = remoteJson,
                localUpdatedAtMillis = localUpdatedAtMillis,
                remoteServerVersion = remoteServerVersion,
                remoteUpdatedAtMillis = remoteUpdatedAtMillis,
                detectedAtMillis = detectedAtMillis,
                status = ConflictStatus.OPEN.name,
                resolutionKind = null,
            )
            queries.lastInsertRowId().awaitAsOne()
        }
    }

    override suspend fun recordAutoResolved(
        entityType: String,
        entityId: String,
        localJson: String,
        remoteJson: String?,
        localUpdatedAtMillis: Long,
        remoteServerVersion: Long,
        remoteUpdatedAtMillis: Long,
        detectedAtMillis: Long,
        resolutionKind: ConflictResolutionKind,
    ) = withContext(dispatcher) {
        queries.insertConflict(
            entityType = entityType,
            entityId = entityId,
            localJson = localJson,
            remoteJson = remoteJson,
            localUpdatedAtMillis = localUpdatedAtMillis,
            remoteServerVersion = remoteServerVersion,
            remoteUpdatedAtMillis = remoteUpdatedAtMillis,
            detectedAtMillis = detectedAtMillis,
            status = ConflictStatus.AUTO_RESOLVED.name,
            resolutionKind = resolutionKind.name,
        )
    }

    override suspend fun findOpen(entityType: String, entityId: String): ConflictRecord? =
        withContext(dispatcher) {
            queries.findOpen(
                entityType = entityType,
                entityId = entityId,
                openStatus = ConflictStatus.OPEN.name,
            ).awaitAsOneOrNull()?.toRecord()
        }

    override suspend fun markUserResolved(id: Long, resolutionKind: ConflictResolutionKind) {
        withContext(dispatcher) {
            queries.markResolved(
                id = id,
                resolvedStatus = ConflictStatus.USER_RESOLVED.name,
                resolutionKind = resolutionKind.name,
            )
        }
    }

    override suspend fun closeOpenForEntity(entityType: String, entityId: String) {
        withContext(dispatcher) {
            queries.closeOpenForEntity(
                entityType = entityType,
                entityId = entityId,
                openStatus = ConflictStatus.OPEN.name,
                autoResolvedStatus = ConflictStatus.AUTO_RESOLVED.name,
                resolutionKind = ConflictResolutionKind.ACCEPT_REMOTE.name,
            )
        }
    }
}