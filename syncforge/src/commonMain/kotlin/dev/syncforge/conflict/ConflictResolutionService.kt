package dev.syncforge.conflict

import dev.syncforge.entity.EntityRegistry
import dev.syncforge.entity.TypedEntitySyncHandler

internal class ConflictResolutionService(
    private val registry: EntityRegistry,
    private val conflictStore: ConflictStore,
    private val conflictApplier: ConflictPullApplier,
) {

    suspend fun resolve(
        entityType: String,
        entityId: String,
        choice: ConflictChoice,
    ) {
        val record = conflictStore.findOpen(entityType, entityId)
            ?: error("No open conflict for entityType='$entityType' entityId='$entityId'")

        val handler = registry.requireHandler(entityType)
        require(handler is TypedEntitySyncHandler<*>) {
            "Conflict resolution requires TypedEntitySyncHandler for entityType='$entityType'"
        }

        @Suppress("UNCHECKED_CAST")
        val typedHandler = handler as TypedEntitySyncHandler<dev.syncforge.entity.SyncedEntity>

        val resolution = choice.toResolution(
            localJson = record.localJson,
            remoteJson = record.remoteJson,
            deserialize = typedHandler::decodePayload,
        )

        conflictApplier.applyResolution(typedHandler, entityId, resolution)
        conflictStore.markUserResolved(record.id, resolution.toKind())
    }
}