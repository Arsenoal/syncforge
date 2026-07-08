package dev.syncforge.debug

import dev.syncforge.api.ExperimentalSyncForgeApi

/**
 * Rolling-window latency percentiles for a sync operation type.
 */
@ExperimentalSyncForgeApi
data class SyncLatencyPercentiles(
    val p50Millis: Long?,
    val p95Millis: Long?,
    val p99Millis: Long?,
    val sampleCount: Int = 0,
) {
    companion object {
        val Empty = SyncLatencyPercentiles(p50Millis = null, p95Millis = null, p99Millis = null, sampleCount = 0)
    }
}

internal fun List<Long>.toLatencyPercentiles(): SyncLatencyPercentiles {
    if (isEmpty()) return SyncLatencyPercentiles.Empty
    val sorted = sorted()
    return SyncLatencyPercentiles(
        p50Millis = sorted.percentileRank(0.50),
        p95Millis = sorted.percentileRank(0.95),
        p99Millis = sorted.percentileRank(0.99),
        sampleCount = size,
    )
}

/** Nearest-rank percentile on a sorted sample. */
private fun List<Long>.percentileRank(p: Double): Long {
    val index = ((size - 1) * p).toInt().coerceIn(0, lastIndex)
    return this[index]
}