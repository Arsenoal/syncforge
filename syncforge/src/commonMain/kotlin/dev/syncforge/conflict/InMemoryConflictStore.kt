package dev.syncforge.conflict

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [ConflictStore] for tests and iOS bootstrap until SQLDelight persistence lands.
 *
 * Does not survive process death — production apps use SQLDelight via platform DSLs.
 */
class InMemoryConflictStore : ConflictStore {

    private val mutex = Mutex()
    private val records = MutableStateFlow<List<ConflictRecord>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<ConflictRecord>> = records

    override fun observeOpen(): Flow<List<ConflictRecord>> =
        records.map { list -> list.filter { it.status == ConflictStatus.OPEN } }

    override suspend fun countOpen(): Int =
        mutex.withLock { records.value.count { it.status == ConflictStatus.OPEN } }

    override suspend fun recordDeferred(
        entityType: String,
        entityId: String,
        localJson: String,
        remoteJson: String?,
        localUpdatedAtMillis: Long,
        remoteServerVersion: Long,
        remoteUpdatedAtMillis: Long,
        detectedAtMillis: Long,
    ): Long = mutex.withLock {
        val id = nextId++
        records.value = records.value + ConflictRecord(
            id = id,
            entityType = entityType,
            entityId = entityId,
            localJson = localJson,
            remoteJson = remoteJson,
            localUpdatedAtMillis = localUpdatedAtMillis,
            remoteServerVersion = remoteServerVersion,
            remoteUpdatedAtMillis = remoteUpdatedAtMillis,
            detectedAtMillis = detectedAtMillis,
            status = ConflictStatus.OPEN,
            resolutionKind = null,
        )
        id
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
    ) = mutex.withLock {
        records.value = records.value + ConflictRecord(
            id = nextId++,
            entityType = entityType,
            entityId = entityId,
            localJson = localJson,
            remoteJson = remoteJson,
            localUpdatedAtMillis = localUpdatedAtMillis,
            remoteServerVersion = remoteServerVersion,
            remoteUpdatedAtMillis = remoteUpdatedAtMillis,
            detectedAtMillis = detectedAtMillis,
            status = ConflictStatus.AUTO_RESOLVED,
            resolutionKind = resolutionKind,
        )
    }

    override suspend fun findOpen(entityType: String, entityId: String): ConflictRecord? =
        mutex.withLock {
            records.value.firstOrNull {
                it.entityType == entityType && it.entityId == entityId && it.status == ConflictStatus.OPEN
            }
        }

    override suspend fun markUserResolved(id: Long, resolutionKind: ConflictResolutionKind) =
        mutex.withLock {
            records.value = records.value.map { record ->
                if (record.id == id) {
                    record.copy(
                        status = ConflictStatus.USER_RESOLVED,
                        resolutionKind = resolutionKind,
                    )
                } else {
                    record
                }
            }
        }

    override suspend fun closeOpenForEntity(entityType: String, entityId: String) =
        mutex.withLock {
            records.value = records.value.map { record ->
                if (record.entityType == entityType &&
                    record.entityId == entityId &&
                    record.status == ConflictStatus.OPEN
                ) {
                    record.copy(
                        status = ConflictStatus.AUTO_RESOLVED,
                        resolutionKind = ConflictResolutionKind.ACCEPT_REMOTE,
                    )
                } else {
                    record
                }
            }
        }
}