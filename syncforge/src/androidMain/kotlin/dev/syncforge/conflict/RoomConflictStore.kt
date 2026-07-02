package dev.syncforge.conflict

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomConflictStore(
    private val dao: ConflictDao,
) : ConflictStore {

    override fun observeAll(): Flow<List<ConflictRecord>> =
        dao.observeAll().map { entries -> entries.map { it.toRecord() } }

    override fun observeOpen(): Flow<List<ConflictRecord>> =
        dao.observeOpen().map { entries -> entries.map { it.toRecord() } }

    override suspend fun countOpen(): Int = dao.countOpen()

    override suspend fun recordDeferred(
        entityType: String,
        entityId: String,
        localJson: String,
        remoteJson: String?,
        localUpdatedAtMillis: Long,
        remoteServerVersion: Long,
        remoteUpdatedAtMillis: Long,
        detectedAtMillis: Long,
    ): Long = dao.insert(
        ConflictEntryEntity(
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
        ),
    )

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
    ) {
        dao.insert(
            ConflictEntryEntity(
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
            ),
        )
    }

    override suspend fun findOpen(entityType: String, entityId: String): ConflictRecord? =
        dao.findOpen(entityType, entityId)?.toRecord()

    override suspend fun markUserResolved(id: Long, resolutionKind: ConflictResolutionKind) {
        dao.markResolved(
            id = id,
            resolvedStatus = ConflictStatus.USER_RESOLVED.name,
            resolutionKind = resolutionKind.name,
        )
    }

    override suspend fun closeOpenForEntity(entityType: String, entityId: String) {
        dao.closeOpenForEntity(
            entityType = entityType,
            entityId = entityId,
            resolutionKind = ConflictResolutionKind.ACCEPT_REMOTE.name,
        )
    }
}