package dev.syncforge.conflict

import dev.syncforge.entity.PullApplyOutcome
import dev.syncforge.entity.RemoteMetadata
import dev.syncforge.entity.TypedEntitySyncHandler
import dev.syncforge.model.SyncState
import dev.syncforge.network.RemoteDelta
import dev.syncforge.sync.OutboxReconciler

internal class ConflictPullApplier(
    private val policy: ConflictPolicy,
    private val conflictStore: ConflictStore,
    private val mergeBaseRecorder: MergeBaseRecorder = MergeBaseRecorder(),
    private val outboxReconciler: OutboxReconciler? = null,
    private val clock: () -> Long = { dev.syncforge.sync.currentTimeMillis() },
) {

    suspend fun <T : dev.syncforge.entity.SyncedEntity> applyDelta(
        handler: TypedEntitySyncHandler<T>,
        delta: RemoteDelta,
    ): PullApplyOutcome {
        val local = handler.findLocal(delta.entityId)
        if (local == null) {
            if (delta.isDeleted) return PullApplyOutcome.SKIPPED
            val remote = delta.payloadJson?.let { handler.decodePayload(it) } ?: return PullApplyOutcome.SKIPPED
            val synced = handler.withSyncState(remote, SyncState.SYNCED)
            handler.persistEntity(synced, insert = true)
            mergeBaseRecorder.recordFromHandler(handler, synced, serverVersion = delta.serverVersion)
            return PullApplyOutcome.INSERTED
        }

        val remoteMeta = RemoteMetadata(
            serverVersion = delta.serverVersion,
            updatedAtMillis = delta.updatedAtMillis,
            isDeleted = delta.isDeleted,
        )
        val remotePayload = delta.payloadJson?.let { handler.decodePayload(it) }

        if (!ConflictDetector.isConflict(local, remoteMeta)) {
            return applyNonConflict(handler, delta, local, remotePayload, remoteMeta)
        }

        val strategy = policy.strategyFor(handler.entityType)
        val mergeBasePayload = mergeBaseRecorder.loadPayload(handler, delta.entityId)
        val context = ConflictContext(
            entityType = handler.entityType,
            local = local,
            remote = remoteMeta,
            remotePayload = remotePayload,
            mergeBasePayload = mergeBasePayload,
        )
        val outcome = strategy.resolve(context)
        val now = clock()
        val localJson = handler.encodePayload(local)
        val remoteJson = remotePayload?.let { handler.encodePayload(it) }

        return when (outcome) {
            is ConflictOutcome.Deferred -> {
                conflictStore.closeOpenForEntity(handler.entityType, delta.entityId)
                conflictStore.recordDeferred(
                    entityType = handler.entityType,
                    entityId = delta.entityId,
                    localJson = localJson,
                    remoteJson = remoteJson,
                    localUpdatedAtMillis = local.updatedAtMillis,
                    remoteServerVersion = remoteMeta.serverVersion,
                    remoteUpdatedAtMillis = remoteMeta.updatedAtMillis,
                    detectedAtMillis = now,
                )
                handler.persistEntity(
                    handler.withSyncState(outcome.local, SyncState.CONFLICT),
                    insert = false,
                )
                PullApplyOutcome.CONFLICT_RESOLVED
            }

            is ConflictOutcome.Resolved -> {
                conflictStore.recordAutoResolved(
                    entityType = handler.entityType,
                    entityId = delta.entityId,
                    localJson = localJson,
                    remoteJson = remoteJson,
                    localUpdatedAtMillis = local.updatedAtMillis,
                    remoteServerVersion = remoteMeta.serverVersion,
                    remoteUpdatedAtMillis = remoteMeta.updatedAtMillis,
                    detectedAtMillis = now,
                    resolutionKind = outcome.resolution.toKind(),
                )
                applyResolution(handler, delta.entityId, outcome.resolution)
            }
        }
    }

    private suspend fun <T : dev.syncforge.entity.SyncedEntity> applyNonConflict(
        handler: TypedEntitySyncHandler<T>,
        delta: RemoteDelta,
        local: T,
        remotePayload: T?,
        remoteMeta: RemoteMetadata,
    ): PullApplyOutcome {
        if (remoteMeta.isDeleted) {
            handler.deleteLocal(delta.entityId)
            mergeBaseRecorder.remove(handler.entityType, delta.entityId)
            return PullApplyOutcome.DELETED
        }
        val remote = remotePayload ?: return PullApplyOutcome.SKIPPED
        val synced = handler.withSyncState(remote, SyncState.SYNCED)
        handler.persistEntity(synced, insert = false)
        mergeBaseRecorder.recordFromHandler(handler, synced, serverVersion = remoteMeta.serverVersion)
        return PullApplyOutcome.UPDATED
    }

    suspend fun <T : dev.syncforge.entity.SyncedEntity> applyResolution(
        handler: TypedEntitySyncHandler<T>,
        entityId: String,
        resolution: ConflictResolution<T>,
    ): PullApplyOutcome {
        val outcome = when (resolution) {
            ConflictResolution.DeleteLocal -> {
                handler.deleteLocal(entityId)
                mergeBaseRecorder.remove(handler.entityType, entityId)
                PullApplyOutcome.DELETED
            }

            is ConflictResolution.KeepLocal -> {
                handler.persistEntity(
                    handler.withSyncState(resolution.entity, SyncState.PENDING),
                    insert = false,
                )
                PullApplyOutcome.CONFLICT_RESOLVED
            }

            is ConflictResolution.AcceptRemote -> {
                val synced = handler.withSyncState(resolution.entity, SyncState.SYNCED)
                handler.persistEntity(synced, insert = false)
                mergeBaseRecorder.recordFromHandler(handler, synced)
                PullApplyOutcome.CONFLICT_RESOLVED
            }

            is ConflictResolution.Merged -> {
                val synced = handler.withSyncState(resolution.entity, SyncState.SYNCED)
                handler.persistEntity(synced, insert = false)
                mergeBaseRecorder.recordFromHandler(handler, synced)
                PullApplyOutcome.CONFLICT_RESOLVED
            }
        }
        outboxReconciler?.reconcile(handler.entityType, entityId, resolution)
        return outcome
    }
}