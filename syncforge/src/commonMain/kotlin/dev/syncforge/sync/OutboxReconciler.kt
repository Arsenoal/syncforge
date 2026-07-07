package dev.syncforge.sync

import dev.syncforge.conflict.ConflictResolution
import dev.syncforge.entity.SyncedEntity
import dev.syncforge.model.Change
import dev.syncforge.outbox.OutboxRepository

/**
 * Aligns the outbox with conflict resolution outcomes (Group 1 §5, job 1.2-11).
 *
 * - [ConflictResolution.AcceptRemote] / [ConflictResolution.DeleteLocal] — drop stale local pushes.
 * - [ConflictResolution.Merged] — replace stale entries with an UPDATE for the merged entity.
 * - [ConflictResolution.KeepLocal] — retain existing outbox rows; enqueue if none remain.
 */
internal class OutboxReconciler(
    private val outbox: OutboxRepository,
    private val optimisticCoordinator: OptimisticSyncCoordinator,
) {

    suspend fun reconcile(
        entityType: String,
        entityId: String,
        resolution: ConflictResolution<*>,
    ) {
        when (resolution) {
            is ConflictResolution.AcceptRemote,
            ConflictResolution.DeleteLocal,
            -> outbox.removeForEntity(entityType, entityId)

            is ConflictResolution.Merged -> {
                outbox.removeForEntity(entityType, entityId)
                enqueueUpdate(entityType, resolution.entity)
            }

            is ConflictResolution.KeepLocal -> {
                if (outbox.findForEntity(entityType, entityId).isEmpty()) {
                    enqueueUpdate(entityType, resolution.entity)
                }
            }
        }
    }

    private suspend fun enqueueUpdate(entityType: String, entity: SyncedEntity) {
        optimisticCoordinator.enqueue(Change.update(entityType, entity))
    }
}