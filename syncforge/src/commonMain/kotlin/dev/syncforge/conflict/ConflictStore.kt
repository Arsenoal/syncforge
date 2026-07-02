package dev.syncforge.conflict

import kotlinx.coroutines.flow.Flow

interface ConflictStore {

    fun observeAll(): Flow<List<ConflictRecord>>

    fun observeOpen(): Flow<List<ConflictRecord>>

    suspend fun countOpen(): Int

    suspend fun recordDeferred(
        entityType: String,
        entityId: String,
        localJson: String,
        remoteJson: String?,
        localUpdatedAtMillis: Long,
        remoteServerVersion: Long,
        remoteUpdatedAtMillis: Long,
        detectedAtMillis: Long,
    ): Long

    suspend fun recordAutoResolved(
        entityType: String,
        entityId: String,
        localJson: String,
        remoteJson: String?,
        localUpdatedAtMillis: Long,
        remoteServerVersion: Long,
        remoteUpdatedAtMillis: Long,
        detectedAtMillis: Long,
        resolutionKind: ConflictResolutionKind,
    )

    suspend fun findOpen(entityType: String, entityId: String): ConflictRecord?

    suspend fun markUserResolved(id: Long, resolutionKind: ConflictResolutionKind)

    suspend fun closeOpenForEntity(entityType: String, entityId: String)
}

object NoOpConflictStore : ConflictStore {

    override fun observeAll(): Flow<List<ConflictRecord>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    override fun observeOpen(): Flow<List<ConflictRecord>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    override suspend fun countOpen(): Int = 0

    override suspend fun recordDeferred(
        entityType: String,
        entityId: String,
        localJson: String,
        remoteJson: String?,
        localUpdatedAtMillis: Long,
        remoteServerVersion: Long,
        remoteUpdatedAtMillis: Long,
        detectedAtMillis: Long,
    ): Long = -1L

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
    ) = Unit

    override suspend fun findOpen(entityType: String, entityId: String): ConflictRecord? = null

    override suspend fun markUserResolved(id: Long, resolutionKind: ConflictResolutionKind) = Unit

    override suspend fun closeOpenForEntity(entityType: String, entityId: String) = Unit
}