package dev.syncforge.debug

import dev.syncforge.model.SyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Rolling-window collector for SyncHealth operational metrics (1.5-02).
 */
internal class SyncMetricsCollector(
    private val maxSamples: Int = 100,
) {
    private val mutex = Mutex()

    private val syncDurations = ArrayDeque<Long>(maxSamples)
    private val pushDurations = ArrayDeque<Long>(maxSamples)
    private val pullDurations = ArrayDeque<Long>(maxSamples)

    private var pullOperationsSampled = 0
    private var conflictsResolvedInWindow = 0
    private var maxOutboxDepth = 0

    private val _snapshot = MutableStateFlow(emptySnapshot())
    val snapshot: StateFlow<SyncMetricsSnapshot> = _snapshot.asStateFlow()

    suspend fun recordOperation(
        type: SyncEventType,
        durationMillis: Long,
        result: SyncResult,
    ) = mutex.withLock {
        when (type) {
            SyncEventType.FULL_SYNC -> append(syncDurations, durationMillis)
            SyncEventType.PUSH -> append(pushDurations, durationMillis)
            SyncEventType.PULL -> append(pullDurations, durationMillis)
            else -> Unit
        }

        if (type == SyncEventType.PULL || type == SyncEventType.FULL_SYNC) {
            val stats = result.syncStats()
            pullOperationsSampled++
            conflictsResolvedInWindow += stats.conflictsResolved
        }
        publishLocked()
    }

    suspend fun recordOutboxDepth(depth: Int) = mutex.withLock {
        if (depth > maxOutboxDepth) {
            maxOutboxDepth = depth
            publishLocked()
        }
    }

    private fun publishLocked() {
        _snapshot.value = SyncMetricsSnapshot(
            syncLatency = syncDurations.toList().toLatencyPercentiles(),
            pushLatency = pushDurations.toList().toLatencyPercentiles(),
            pullLatency = pullDurations.toList().toLatencyPercentiles(),
            conflictRate = conflictRateLocked(),
            pullOperationsSampled = pullOperationsSampled,
            maxOutboxDepth = maxOutboxDepth,
        )
    }

    private fun conflictRateLocked(): Double? =
        if (pullOperationsSampled == 0) {
            null
        } else {
            conflictsResolvedInWindow.toDouble() / pullOperationsSampled
        }

    private fun append(buffer: ArrayDeque<Long>, value: Long) {
        if (buffer.size >= maxSamples) {
            buffer.removeFirst()
        }
        buffer.addLast(value)
    }

    private fun emptySnapshot() = SyncMetricsSnapshot(
        syncLatency = SyncLatencyPercentiles.Empty,
        pushLatency = SyncLatencyPercentiles.Empty,
        pullLatency = SyncLatencyPercentiles.Empty,
        conflictRate = null,
        pullOperationsSampled = 0,
        maxOutboxDepth = 0,
    )
}

internal data class SyncMetricsSnapshot(
    val syncLatency: SyncLatencyPercentiles,
    val pushLatency: SyncLatencyPercentiles,
    val pullLatency: SyncLatencyPercentiles,
    val conflictRate: Double?,
    val pullOperationsSampled: Int,
    val maxOutboxDepth: Int,
)

private data class SyncResultStats(
    val pulled: Int,
    val conflictsResolved: Int,
)

private fun SyncResult.syncStats(): SyncResultStats =
    when (this) {
        is SyncResult.Success -> SyncResultStats(pulled = pulled, conflictsResolved = conflictsResolved)
        is SyncResult.Partial -> success.syncStats()
        is SyncResult.Failure -> SyncResultStats(pulled = 0, conflictsResolved = 0)
    }