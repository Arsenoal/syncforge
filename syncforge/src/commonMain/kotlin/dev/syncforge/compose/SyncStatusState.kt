package dev.syncforge.compose

import dev.syncforge.model.SyncStatus

/**
 * Platform-agnostic sync status view model for UI layers.
 *
 * Compose observation helpers live in `androidMain` — see [collectSyncStatusUiModel].
 */
data class SyncStatusUiModel(
    val label: String,
    val isSyncing: Boolean,
    val isError: Boolean,
    val pendingCount: Int = 0,
    val conflictCount: Int = 0,
)

fun SyncStatus.toUiModel(): SyncStatusUiModel = when (this) {
    SyncStatus.Idle -> SyncStatusUiModel("Up to date", isSyncing = false, isError = false)
    is SyncStatus.Syncing -> SyncStatusUiModel("Syncing…", isSyncing = true, isError = false)
    is SyncStatus.Pending -> {
        val failedSuffix = if (permanentlyFailedCount > 0) {
            " · $permanentlyFailedCount failed"
        } else {
            ""
        }
        val conflictSuffix = if (conflictCount > 0) {
            if (conflictCount == 1) " · 1 conflict" else " · $conflictCount conflicts"
        } else {
            ""
        }
        val pendingLabel = when {
            outboxCount == 0 && conflictCount > 0 ->
                if (conflictCount == 1) "1 conflict needs resolution" else "$conflictCount conflicts need resolution"
            outboxCount == 1 -> "1 change pending$failedSuffix$conflictSuffix"
            else -> "$outboxCount changes pending$failedSuffix$conflictSuffix"
        }
        SyncStatusUiModel(
            label = pendingLabel,
            isSyncing = false,
            isError = permanentlyFailedCount > 0,
            pendingCount = outboxCount,
            conflictCount = conflictCount,
        )
    }
    is SyncStatus.Offline -> SyncStatusUiModel(
        label = if (outboxCount == 1) "Offline · 1 change queued" else "Offline · $outboxCount changes queued",
        isSyncing = false,
        isError = false,
        pendingCount = outboxCount,
    )
    is SyncStatus.LastSynced -> SyncStatusUiModel("Synced", isSyncing = false, isError = false)
    is SyncStatus.Error -> SyncStatusUiModel(message, isSyncing = false, isError = true)
}