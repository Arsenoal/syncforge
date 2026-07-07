package dev.syncforge.persistence

import dev.syncforge.conflict.MergeBaseSnapshot
import dev.syncforge.conflict.MergeBaseStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SQLDelight-backed [MergeBaseStore] — default when using [SyncForgePersistence].
 */
internal class SqlDelightMergeBaseStore(
    private val database: SyncForgePersistenceDatabase,
) : MergeBaseStore {

    private val queries get() = database.mergeBaseQueries
    private val dispatcher = Dispatchers.Default

    override suspend fun get(entityType: String, entityId: String): MergeBaseSnapshot? =
        withContext(dispatcher) {
            queries.findMergeBase(entityType = entityType, entityId = entityId)
                .executeAsOneOrNull()
                ?.toSnapshot()
        }

    override suspend fun put(snapshot: MergeBaseSnapshot) = withContext(dispatcher) {
        queries.upsertMergeBase(
            entityType = snapshot.entityType,
            entityId = snapshot.entityId,
            payloadJson = snapshot.payloadJson,
            serverVersion = snapshot.serverVersion,
            updatedAtMillis = snapshot.updatedAtMillis,
            storedAtMillis = snapshot.storedAtMillis,
        )
    }

    override suspend fun remove(entityType: String, entityId: String) = withContext(dispatcher) {
        queries.deleteMergeBase(entityType = entityType, entityId = entityId)
    }
}

private fun Syncforge_merge_base.toSnapshot(): MergeBaseSnapshot =
    MergeBaseSnapshot(
        entityType = entityType,
        entityId = entityId,
        payloadJson = payloadJson,
        serverVersion = serverVersion,
        updatedAtMillis = updatedAtMillis,
        storedAtMillis = storedAtMillis,
    )