package dev.syncforge.conflict

import dev.syncforge.entity.SyncedEntity
import dev.syncforge.entity.TypedEntitySyncHandler
import dev.syncforge.sync.currentTimeMillis

/**
 * Records merge-base snapshots after successful sync (push ack or pull apply).
 */
internal class MergeBaseRecorder(
    private val store: MergeBaseStore = NoOpMergeBaseStore,
    private val clock: () -> Long = { currentTimeMillis() },
) {

    suspend fun recordEntity(
        entityType: String,
        entityId: String,
        payloadJson: String,
        updatedAtMillis: Long,
        serverVersion: Long? = null,
    ) {
        store.put(
            MergeBaseSnapshot(
                entityType = entityType,
                entityId = entityId,
                payloadJson = payloadJson,
                serverVersion = serverVersion,
                updatedAtMillis = updatedAtMillis,
                storedAtMillis = clock(),
            ),
        )
    }

    suspend fun <T : SyncedEntity> recordFromHandler(
        handler: TypedEntitySyncHandler<T>,
        entity: T,
        serverVersion: Long? = null,
    ) {
        recordEntity(
            entityType = handler.entityType,
            entityId = entity.id,
            payloadJson = handler.encodePayload(entity),
            updatedAtMillis = entity.updatedAtMillis,
            serverVersion = serverVersion,
        )
    }

    suspend fun recordSyncedLocal(
        handler: TypedEntitySyncHandler<*>,
        entityId: String,
        serverVersion: Long? = null,
    ) {
        val entity = handler.findLocal(entityId) ?: return
        recordEntity(
            entityType = handler.entityType,
            entityId = entity.id,
            payloadJson = handler.encodePayloadForMergeBase(entity),
            updatedAtMillis = entity.updatedAtMillis,
            serverVersion = serverVersion,
        )
    }

    suspend fun remove(entityType: String, entityId: String) {
        store.remove(entityType, entityId)
    }
}