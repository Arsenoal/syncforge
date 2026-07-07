package dev.syncforge.sync

import dev.syncforge.conflict.MergeBaseRecorder
import dev.syncforge.conflict.MergeBaseStore
import dev.syncforge.conflict.NoOpMergeBaseStore
import dev.syncforge.entity.EntityRegistry
import dev.syncforge.entity.TypedEntitySyncHandler
import dev.syncforge.model.SyncError
import dev.syncforge.model.SyncResult
import dev.syncforge.model.SyncStatus
import dev.syncforge.network.SyncTransport
import dev.syncforge.outbox.OutboxRepository

/**
 * Internal coordinator for push/pull cycles.
 */
internal class SyncEngine(
    private val config: SyncConfig,
    private val outbox: OutboxRepository,
    private val transport: SyncTransport,
    private val registry: EntityRegistry,
    private val conflictStore: dev.syncforge.conflict.ConflictStore = dev.syncforge.conflict.NoOpConflictStore,
    private val conflictPolicy: dev.syncforge.conflict.ConflictPolicy = dev.syncforge.conflict.ConflictPolicy.Default,
    private val mergeBaseRecorder: MergeBaseRecorder = MergeBaseRecorder(NoOpMergeBaseStore),
    private val pullDeltaApplier: PullDeltaApplier = PullDeltaApplier(
        registry,
        conflictPolicy,
        conflictStore,
        mergeBaseRecorder,
    ),
    private val clock: () -> Long = { currentTimeMillis() },
) {

    constructor(
        config: SyncConfig,
        outbox: OutboxRepository,
        transport: SyncTransport,
        registry: EntityRegistry,
        conflictStore: dev.syncforge.conflict.ConflictStore,
        conflictPolicy: dev.syncforge.conflict.ConflictPolicy,
        mergeBaseStore: MergeBaseStore,
        clock: () -> Long = { currentTimeMillis() },
    ) : this(
        config = config,
        outbox = outbox,
        transport = transport,
        registry = registry,
        conflictStore = conflictStore,
        conflictPolicy = conflictPolicy,
        mergeBaseRecorder = MergeBaseRecorder(mergeBaseStore, clock),
        clock = clock,
    )

    suspend fun runFullSync(lastSyncCursor: Long): SyncResult {
        val pushResult = runPush()
        if (pushResult is SyncResult.Failure && !isConflictOnlyFailure(pushResult)) {
            return pushResult
        }

        val pullResult = runPull(lastSyncCursor)
        return combine(pushResult, pullResult)
    }

    suspend fun runPush(): SyncResult {
        val now = clock()
        val batch = outbox.peek(config.pushBatchSize, now)
        if (batch.isEmpty()) {
            return SyncResult.Success()
        }

        return try {
            val response = transport.push(batch)

            batch.filter { it.id in response.acknowledgedIds }.forEach { entry ->
                val handler = registry.requireHandler(entry.entityType)
                handler.onPushAcknowledged(entry.entityId)
                if (entry.isDelete) {
                    mergeBaseRecorder.remove(entry.entityType, entry.entityId)
                } else if (handler is TypedEntitySyncHandler<*>) {
                    mergeBaseRecorder.recordSyncedLocal(handler, entry.entityId)
                }
            }
            outbox.markAcknowledged(response.acknowledgedIds)

            val errors = mutableListOf<SyncError>()
            response.rejected.forEach { rejection ->
                val entry = batch.first { it.id == rejection.outboxId }
                if (rejection.error.code != SyncError.Code.CONFLICT) {
                    registry.requireHandler(entry.entityType).rollbackEntry(entry)
                }
                val retryable = SyncErrorPolicy.isRetryable(rejection.error.code)
                outbox.markFailed(
                    id = rejection.outboxId,
                    error = rejection.error.message,
                    retryable = retryable,
                    maxRetries = config.maxRetries,
                )
                errors += rejection.error
            }

            when {
                errors.isEmpty() -> SyncResult.Success(pushed = response.acknowledgedIds.size)
                response.acknowledgedIds.isNotEmpty() -> SyncResult.Partial(
                    success = SyncResult.Success(pushed = response.acknowledgedIds.size),
                    errors = errors,
                )
                errors.all { it.code == SyncError.Code.CONFLICT } -> SyncResult.Partial(
                    success = SyncResult.Success(),
                    errors = errors,
                )
                else -> SyncResult.Failure(errors.first())
            }
        } catch (e: Exception) {
            val error = SyncError(
                code = SyncError.Code.NETWORK,
                message = e.message ?: "Push failed",
                cause = e,
            )
            batch.forEach { entry ->
                outbox.markFailed(
                    id = entry.id,
                    error = error.message,
                    retryable = true,
                    maxRetries = config.maxRetries,
                )
            }
            SyncResult.Failure(error)
        }
    }

    suspend fun runPull(sinceTimestampMillis: Long): SyncResult {
        return try {
            var pageCursor: String? = null
            var totalPulled = 0
            var conflictsResolved = 0
            var deleted = 0
            var serverTimestamp = sinceTimestampMillis
            var hasMore: Boolean

            do {
                val response = transport.pull(
                    sinceTimestampMillis = sinceTimestampMillis,
                    entityTypes = config.entityTypes,
                    pageSize = config.pullPageSize,
                    pageCursor = pageCursor,
                )
                val stats = pullDeltaApplier.applyAll(response.deltas)
                totalPulled += response.deltas.size
                conflictsResolved += stats.conflictsResolved
                deleted += stats.deleted
                serverTimestamp = response.serverTimestampMillis
                pageCursor = response.nextPageCursor
                hasMore = response.hasMore
            } while (hasMore)

            SyncResult.Success(
                pulled = totalPulled,
                conflictsResolved = conflictsResolved,
                deleted = deleted,
                syncCursorMillis = serverTimestamp,
            )
        } catch (e: Exception) {
            SyncResult.Failure(
                SyncError(
                    code = SyncError.Code.NETWORK,
                    message = e.message ?: "Pull failed",
                    cause = e,
                ),
            )
        }
    }

    suspend fun resolveStatus(
        networkOnline: Boolean,
        lastSyncedAt: Long?,
        openConflictCount: Int = 0,
    ): SyncStatus {
        val awaiting = outbox.countAwaitingPush()
        val failed = outbox.countPermanentlyFailed(config.maxRetries)
        val readyNow = outbox.peek(config.pushBatchSize, clock()).size

        return when {
            !networkOnline && awaiting > 0 -> SyncStatus.Offline(awaiting)
            readyNow > 0 || (awaiting > failed) || openConflictCount > 0 -> SyncStatus.Pending(
                outboxCount = awaiting,
                permanentlyFailedCount = failed,
                conflictCount = openConflictCount,
            )
            failed > 0 -> SyncStatus.Error(
                message = "$failed change(s) failed permanently",
                retryable = false,
            )
            lastSyncedAt != null -> SyncStatus.LastSynced(lastSyncedAt)
            else -> SyncStatus.Idle
        }
    }

    private fun combine(push: SyncResult, pull: SyncResult): SyncResult {
        if (pull is SyncResult.Failure) return pull
        if (push is SyncResult.Failure) {
            return if (isConflictOnlyFailure(push) && pull is SyncResult.Success) {
                pull
            } else {
                push
            }
        }

        val pullSuccess = pull as? SyncResult.Success
            ?: return SyncResult.Failure(
                SyncError(SyncError.Code.UNKNOWN, "Unexpected sync result combination"),
            )

        return when (push) {
            is SyncResult.Success ->
                SyncResult.Success(
                    pushed = push.pushed,
                    pulled = pullSuccess.pulled,
                    conflictsResolved = push.conflictsResolved + pullSuccess.conflictsResolved,
                    deleted = push.deleted + pullSuccess.deleted,
                    syncCursorMillis = pullSuccess.syncCursorMillis,
                )

            is SyncResult.Partial ->
                SyncResult.Partial(
                    success = SyncResult.Success(
                        pushed = push.success.pushed,
                        pulled = pullSuccess.pulled,
                        conflictsResolved = push.success.conflictsResolved + pullSuccess.conflictsResolved,
                        deleted = push.success.deleted + pullSuccess.deleted,
                        syncCursorMillis = pullSuccess.syncCursorMillis,
                    ),
                    errors = push.errors,
                )

            is SyncResult.Failure ->
                SyncResult.Failure(
                    SyncError(SyncError.Code.UNKNOWN, "Unexpected sync result combination"),
                )
        }
    }

    private fun isConflictOnlyFailure(result: SyncResult.Failure): Boolean =
        result.error.code == SyncError.Code.CONFLICT
}

/** Platform clock — actual impl in androidMain / jvmMain. */
internal expect fun currentTimeMillis(): Long