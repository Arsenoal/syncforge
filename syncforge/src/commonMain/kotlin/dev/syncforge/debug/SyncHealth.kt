package dev.syncforge.debug

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.model.SyncStatus

/**
 * Point-in-time snapshot of sync subsystem health for debug dashboards.
 */
@ExperimentalSyncForgeApi
data class SyncHealth(
    val status: SyncStatus,
    val isOnline: Boolean,
    val pendingOutboxCount: Int,
    val failedOutboxCount: Int,
    val openConflictCount: Int,
    val lastSyncedAtMillis: Long?,
    val pullCursorMillis: Long,
    val maxRetries: Int,
    /** Total outbox rows (pending, retrying, and permanently failed). */
    val outboxDepth: Int,
    /** Peak [outboxDepth] observed since process start. */
    val maxOutboxDepth: Int,
    val syncLatency: SyncLatencyPercentiles = SyncLatencyPercentiles.Empty,
    val pushLatency: SyncLatencyPercentiles = SyncLatencyPercentiles.Empty,
    val pullLatency: SyncLatencyPercentiles = SyncLatencyPercentiles.Empty,
    /**
     * Average conflicts resolved per pull in the metrics window.
     * `null` when no pull/full-sync samples exist yet.
     */
    val conflictRate: Double? = null,
    val pullOperationsSampled: Int = 0,
) {
    val isSyncing: Boolean get() = status is SyncStatus.Syncing
}