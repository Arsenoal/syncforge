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
) {
    val isSyncing: Boolean get() = status is SyncStatus.Syncing
}