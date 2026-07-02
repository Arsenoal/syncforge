package dev.syncforge.sync

import dev.syncforge.entity.EntityRegistry
import dev.syncforge.model.Change
import dev.syncforge.outbox.OutboxRepository

/**
 * Handles enqueue: snapshot → optimistic Room write → outbox persist.
 */
internal class OptimisticSyncCoordinator(
    private val config: SyncConfig,
    private val registry: EntityRegistry,
    private val outbox: OutboxRepository,
) {

    suspend fun enqueue(change: Change<*>) {
        val handler = registry.requireHandler(change.entityType)
        val snapshot = if (config.enableOptimisticUpdates) {
            handler.captureSnapshot(change.entityId)
        } else {
            null
        }

        if (config.enableOptimisticUpdates) {
            handler.applyOptimistic(change)
        }

        val payloadJson = handler.serializeChange(change)
        outbox.enqueue(
            change = change,
            payloadJson = payloadJson,
            rollbackSnapshotJson = snapshot,
        )
    }
}