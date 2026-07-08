package dev.syncforge.debug

import dev.syncforge.conflict.ConflictStore
import dev.syncforge.model.SyncResult
import dev.syncforge.model.SyncStatus
import dev.syncforge.network.NetworkMonitor
import dev.syncforge.outbox.OutboxRepository
import dev.syncforge.sync.SyncConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class SyncDebugImpl(
    private val outbox: OutboxRepository,
    private val conflictStore: ConflictStore,
    private val networkMonitor: NetworkMonitor,
    private val config: SyncConfig,
    private val eventLog: SyncEventLog,
    private val metrics: SyncMetricsCollector,
    private val status: StateFlow<SyncStatus>,
    private val pullCursor: StateFlow<Long>,
    private val onResetPullCursor: suspend () -> Unit,
    scope: CoroutineScope,
) : SyncDebug {

    init {
        scope.launch {
            outbox.observeAll().collect { entries ->
                metrics.recordOutboxDepth(entries.size)
            }
        }
    }

    override val health: StateFlow<SyncHealth> =
        combine(
            combine(
                status,
                pullCursor,
                outbox.observePendingCount(),
                outbox.observeAll(),
                conflictStore.observeOpen(),
            ) { currentStatus, cursor, pendingCount, allEntries, openConflicts ->
                HealthInputs(
                    status = currentStatus,
                    cursor = cursor,
                    pendingCount = pendingCount,
                    allEntries = allEntries,
                    openConflictCount = openConflicts.size,
                )
            },
            metrics.snapshot,
            networkMonitor.observeOnline(),
            eventLog.events,
        ) { inputs, metricsSnapshot, isOnline, events ->
            val enriched = inputs.copy(metrics = metricsSnapshot)
            val failed = enriched.allEntries.count { it.isPermanentlyFailed(config.maxRetries) }
            val lastSynced = when (enriched.status) {
                is SyncStatus.LastSynced -> enriched.status.timestampMillis
                else -> enriched.cursor.takeIf { it > 0 }
            }
            val outboxDepth = enriched.allEntries.size
            SyncHealth(
                status = enriched.status,
                isOnline = isOnline,
                pendingOutboxCount = enriched.pendingCount,
                failedOutboxCount = failed,
                openConflictCount = enriched.openConflictCount,
                lastSyncedAtMillis = lastSynced,
                pullCursorMillis = enriched.cursor,
                maxRetries = config.maxRetries,
                outboxDepth = outboxDepth,
                maxOutboxDepth = maxOf(enriched.metrics.maxOutboxDepth, outboxDepth),
                syncLatency = enriched.metrics.syncLatency,
                pushLatency = enriched.metrics.pushLatency,
                pullLatency = enriched.metrics.pullLatency,
                conflictRate = enriched.metrics.conflictRate,
                pullOperationsSampled = enriched.metrics.pullOperationsSampled,
                errorBreakdown = events.toErrorBreakdown(),
            )
        }.stateIn(
            scope,
            SharingStarted.WhileSubscribed(5_000),
            defaultSyncHealth(config.maxRetries),
        )

    override val outboxItems: StateFlow<List<dev.syncforge.model.OutboxEntry>> =
        outbox.observeAll()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    override val events: StateFlow<List<SyncEvent>> = eventLog.events

    override val conflictRecords: StateFlow<List<dev.syncforge.conflict.ConflictRecord>> =
        conflictStore.observeAll()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    override suspend fun clearOutbox() {
        outbox.clear()
        eventLog.record(
            type = SyncEventType.OUTBOX_CLEARED,
            success = true,
            summary = "Outbox cleared manually",
        )
    }

    override suspend fun clearEventLog() {
        eventLog.clear()
    }

    override suspend fun resetPullCursor() {
        onResetPullCursor()
    }

    suspend fun recordSyncResult(
        type: SyncEventType,
        result: SyncResult,
        durationMillis: Long,
    ) {
        metrics.recordOperation(type, durationMillis, result)
        when (result) {
            is SyncResult.Success -> eventLog.record(
                type = type,
                success = true,
                summary = result.toDebugSummary(),
                durationMillis = durationMillis,
            )
            is SyncResult.Partial -> eventLog.record(
                type = type,
                success = false,
                summary = "${result.success.toDebugSummary()} · ${result.errors.size} error(s)",
                errorCode = result.errors.firstOrNull()?.code,
                durationMillis = durationMillis,
            )
            is SyncResult.Failure -> eventLog.record(
                type = type,
                success = false,
                summary = result.error.message,
                errorCode = result.error.code,
                durationMillis = durationMillis,
            )
        }
    }
}

private fun defaultSyncHealth(maxRetries: Int): SyncHealth =
    SyncHealth(
        status = SyncStatus.Idle,
        isOnline = true,
        pendingOutboxCount = 0,
        failedOutboxCount = 0,
        openConflictCount = 0,
        lastSyncedAtMillis = null,
        pullCursorMillis = 0,
        maxRetries = maxRetries,
        outboxDepth = 0,
        maxOutboxDepth = 0,
    )

private data class HealthInputs(
    val status: SyncStatus,
    val cursor: Long,
    val pendingCount: Int,
    val allEntries: List<dev.syncforge.model.OutboxEntry>,
    val openConflictCount: Int,
    val metrics: SyncMetricsSnapshot = SyncMetricsSnapshot(
        syncLatency = SyncLatencyPercentiles.Empty,
        pushLatency = SyncLatencyPercentiles.Empty,
        pullLatency = SyncLatencyPercentiles.Empty,
        conflictRate = null,
        pullOperationsSampled = 0,
        maxOutboxDepth = 0,
    ),
)

private fun SyncResult.Success.toDebugSummary(): String = buildString {
    append("OK")
    if (pushed > 0) append(" · pushed $pushed")
    if (pulled > 0) append(" · pulled $pulled")
    if (conflictsResolved > 0) append(" · conflicts $conflictsResolved")
    if (deleted > 0) append(" · deleted $deleted")
}