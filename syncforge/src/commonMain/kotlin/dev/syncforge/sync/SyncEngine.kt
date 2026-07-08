package dev.syncforge.sync

import dev.syncforge.conflict.ConflictPullApplier
import dev.syncforge.conflict.MergeBaseRecorder
import dev.syncforge.conflict.MergeBaseStore
import dev.syncforge.conflict.NoOpConflictStore
import dev.syncforge.conflict.NoOpMergeBaseStore
import dev.syncforge.entity.EntityRegistry
import dev.syncforge.entity.TypedEntitySyncHandler
import dev.syncforge.model.SyncError
import dev.syncforge.model.SyncResult
import dev.syncforge.model.SyncStatus
import dev.syncforge.network.SyncTransport
import dev.syncforge.network.SyncTransportException
import dev.syncforge.outbox.OutboxRepository
import dev.syncforge.trace.SyncSpanName
import dev.syncforge.trace.SyncTraceAttributes
import dev.syncforge.trace.SyncTracer
import dev.syncforge.trace.NoOpSyncTracer
import dev.syncforge.trace.recordSyncResult
import dev.syncforge.trace.runSuspendSpan
import dev.syncforge.trace.syncSpanStatusFor

/**
 * Internal coordinator for push/pull cycles.
 */
internal class SyncEngine(
    private val config: SyncConfig,
    private val outbox: OutboxRepository,
    private val transport: SyncTransport,
    private val registry: EntityRegistry,
    private val conflictStore: dev.syncforge.conflict.ConflictStore = NoOpConflictStore,
    private val mergeBaseRecorder: MergeBaseRecorder = MergeBaseRecorder(NoOpMergeBaseStore),
    private val conflictPullApplier: ConflictPullApplier = ConflictPullApplier(
        dev.syncforge.conflict.ConflictPolicy.Default,
        conflictStore,
        mergeBaseRecorder,
    ),
    private val pullDeltaApplier: PullDeltaApplier = PullDeltaApplier(registry, conflictPullApplier),
    private val tracer: SyncTracer = NoOpSyncTracer,
    private val clock: () -> Long = { currentTimeMillis() },
) {

    constructor(
        config: SyncConfig,
        outbox: OutboxRepository,
        transport: SyncTransport,
        registry: EntityRegistry,
        conflictStore: dev.syncforge.conflict.ConflictStore,
        mergeBaseStore: MergeBaseStore,
        conflictPullApplier: ConflictPullApplier,
        tracer: SyncTracer = NoOpSyncTracer,
        clock: () -> Long = { currentTimeMillis() },
    ) : this(
        config = config,
        outbox = outbox,
        transport = transport,
        registry = registry,
        conflictStore = conflictStore,
        mergeBaseRecorder = MergeBaseRecorder(mergeBaseStore, clock),
        conflictPullApplier = conflictPullApplier,
        tracer = tracer,
        clock = clock,
    )

    suspend fun runFullSync(lastSyncCursor: Long): SyncResult {
        val pushResult = runPush()
        if (pushResult is SyncResult.Failure && !isConflictOnlyFailure(pushResult)) {
            return pushResult
        }

        val pullResult = runPull(lastSyncCursor)
        if (pullResult is SyncResult.Failure) return pullResult

        val trailingPushResult = if (outbox.peek(config.pushBatchSize, clock()).isEmpty()) {
            SyncResult.Success()
        } else {
            runPush()
        }
        return combine(combine(pushResult, pullResult), trailingPushResult)
    }

    suspend fun runPush(): SyncResult {
        val now = clock()
        val batch = outbox.peek(config.pushBatchSize, now)
        if (batch.isEmpty()) {
            return SyncResult.Success()
        }

        return tracer.runSuspendSpan(
            name = SyncSpanName.PUSH,
            attributes = mapOf(
                SyncTraceAttributes.OPERATION to "push",
                SyncTraceAttributes.BATCH_SIZE to batch.size.toString(),
            ),
            statusFor = ::syncSpanStatusFor,
        ) { span ->
            runPushInner(batch, span)
        }
    }

    private suspend fun runPushInner(batch: List<dev.syncforge.model.OutboxEntry>, span: dev.syncforge.trace.SyncSpan): SyncResult =
        try {
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
                    retryAtMillis = computeRetryAtMillis(entry, rejection.error, retryable),
                )
                errors += rejection.error
            }

            span.setAttribute(SyncTraceAttributes.ACKNOWLEDGED_COUNT, response.acknowledgedIds.size.toLong())
            span.setAttribute(SyncTraceAttributes.REJECTED_COUNT, response.rejected.size.toLong())

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
            }.also { span.recordSyncResult(it) }
        } catch (e: Exception) {
            val error = transportErrorFrom(e, defaultMessage = "Push failed")
            val retryable = SyncErrorPolicy.isRetryable(error.code)
            batch.forEach { entry ->
                outbox.markFailed(
                    id = entry.id,
                    error = error.message,
                    retryable = retryable,
                    maxRetries = config.maxRetries,
                    retryAtMillis = computeRetryAtMillis(entry, error, retryable),
                )
            }
            SyncResult.Failure(error).also { span.recordSyncResult(it) }
        }

    suspend fun runPull(sinceTimestampMillis: Long): SyncResult =
        tracer.runSuspendSpan(
            name = SyncSpanName.PULL,
            attributes = mapOf(
                SyncTraceAttributes.OPERATION to "pull",
                SyncTraceAttributes.SINCE_MILLIS to sinceTimestampMillis.toString(),
            ),
            statusFor = ::syncSpanStatusFor,
        ) { span ->
            runPullInner(sinceTimestampMillis, span)
        }

    private suspend fun runPullInner(sinceTimestampMillis: Long, span: dev.syncforge.trace.SyncSpan): SyncResult =
        try {
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
            ).also {
                span.setAttribute(SyncTraceAttributes.PULLED_COUNT, totalPulled.toLong())
                span.setAttribute(SyncTraceAttributes.CONFLICTS_RESOLVED, conflictsResolved.toLong())
                span.setAttribute(SyncTraceAttributes.DELETED_COUNT, deleted.toLong())
                span.recordSyncResult(it)
            }
        } catch (e: Exception) {
            SyncResult.Failure(
                transportErrorFrom(e, defaultMessage = "Pull failed"),
            ).also { span.recordSyncResult(it) }
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

    private fun transportErrorFrom(e: Exception, defaultMessage: String): SyncError =
        when (e) {
            is SyncTransportException -> e.error
            else -> SyncError(
                code = SyncError.Code.NETWORK,
                message = e.message ?: defaultMessage,
                cause = e,
            )
        }

    private fun computeRetryAtMillis(
        entry: dev.syncforge.model.OutboxEntry,
        error: SyncError,
        retryable: Boolean,
    ): Long? {
        if (!retryable) return null
        val newRetryCount = entry.retryCount + 1
        if (newRetryCount >= config.maxRetries) return null
        return config.backoffPolicy.nextRetryAtMillis(
            retryCount = newRetryCount,
            nowMillis = clock(),
            serverRetryAfterMillis = error.retryAfterMillis,
        )
    }
}

/** Platform clock — actual impl in androidMain / jvmMain. */
internal expect fun currentTimeMillis(): Long