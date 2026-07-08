package dev.syncforge.debug

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.model.SyncError

/**
 * Counts of failed sync events grouped by [SyncError.Code] (rolling event log window).
 */
@ExperimentalSyncForgeApi
data class SyncErrorBreakdown(
    val byCode: Map<SyncError.Code, Int>,
    val totalFailures: Int,
) {
    companion object {
        val Empty = SyncErrorBreakdown(emptyMap(), 0)
    }
}

internal fun List<SyncEvent>.toErrorBreakdown(): SyncErrorBreakdown {
    val failures = filter { !it.success && it.errorCode != null }
    if (failures.isEmpty()) return SyncErrorBreakdown.Empty
    return SyncErrorBreakdown(
        byCode = failures.groupingBy { it.errorCode!! }.eachCount(),
        totalFailures = failures.size,
    )
}