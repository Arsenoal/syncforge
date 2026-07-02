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

internal class SyncDebugImpl(
    private val outbox: OutboxRepository,
    private val conflictStore: ConflictStore,
    private val networkMonitor: NetworkMonitor,
    private val config: SyncConfig,
    private val eventLog: SyncEventLog,
    private val status: StateFlow<SyncStatus>,
    private val pullCursor: StateFlow<Long>,
    scope: CoroutineScope,
) : SyncDebug {

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
            networkMonitor.observeOnline(),
        ) { inputs, isOnline ->
            val failed = inputs.allEntries.count { it.isPermanentlyFailed(config.maxRetries) }
            val lastSynced = when (inputs.status) {
                is SyncStatus.LastSynced -> inputs.status.timestampMillis
                else -> inputs.cursor.takeIf { it > 0 }
            }
            SyncHealth(
                status = inputs.status,
                isOnline = isOnline,
                pendingOutboxCount = inputs.pendingCount,
                failedOutboxCount = failed,
                openConflictCount = inputs.openConflictCount,
                lastSyncedAtMillis = lastSynced,
                pullCursorMillis = inputs.cursor,
                maxRetries = config.maxRetries,
            )
        }.stateIn(
            scope,
            SharingStarted.WhileSubscribed(5_000),
            SyncHealth(
                status = SyncStatus.Idle,
                isOnline = true,
                pendingOutboxCount = 0,
                failedOutboxCount = 0,
                openConflictCount = 0,
                lastSyncedAtMillis = null,
                pullCursorMillis = 0,
                maxRetries = config.maxRetries,
            ),
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

    suspend fun recordSyncResult(type: SyncEventType, result: SyncResult) {
        when (result) {
            is SyncResult.Success -> eventLog.record(
                type = type,
                success = true,
                summary = result.toDebugSummary(),
            )
            is SyncResult.Partial -> eventLog.record(
                type = type,
                success = false,
                summary = "${result.success.toDebugSummary()} · ${result.errors.size} error(s)",
                errorCode = result.errors.firstOrNull()?.code,
            )
            is SyncResult.Failure -> eventLog.record(
                type = type,
                success = false,
                summary = result.error.message,
                errorCode = result.error.code,
            )
        }
    }
}

private data class HealthInputs(
    val status: SyncStatus,
    val cursor: Long,
    val pendingCount: Int,
    val allEntries: List<dev.syncforge.model.OutboxEntry>,
    val openConflictCount: Int,
)

private fun SyncResult.Success.toDebugSummary(): String = buildString {
    append("OK")
    if (pushed > 0) append(" · pushed $pushed")
    if (pulled > 0) append(" · pulled $pulled")
    if (conflictsResolved > 0) append(" · conflicts $conflictsResolved")
    if (deleted > 0) append(" · deleted $deleted")
}