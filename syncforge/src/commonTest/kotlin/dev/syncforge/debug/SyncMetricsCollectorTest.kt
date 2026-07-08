@file:OptIn(dev.syncforge.api.ExperimentalSyncForgeApi::class)

package dev.syncforge.debug

import dev.syncforge.model.SyncResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncMetricsCollectorTest {

    @Test
    fun recordOperation_tracksLatencyPercentiles() = runTest {
        val collector = SyncMetricsCollector(maxSamples = 10)

        collector.recordOperation(SyncEventType.PUSH, durationMillis = 10, result = SyncResult.Success())
        collector.recordOperation(SyncEventType.PUSH, durationMillis = 20, result = SyncResult.Success())
        collector.recordOperation(SyncEventType.PUSH, durationMillis = 30, result = SyncResult.Success())

        val snapshot = collector.snapshot.value

        assertEquals(20L, snapshot.pushLatency.p50Millis)
        assertEquals(3, snapshot.pushLatency.sampleCount)
        assertEquals(0, snapshot.syncLatency.sampleCount)
    }

    @Test
    fun recordOperation_computesConflictRateFromPulls() = runTest {
        val collector = SyncMetricsCollector()

        collector.recordOperation(
            type = SyncEventType.PULL,
            durationMillis = 5,
            result = SyncResult.Success(pulled = 2, conflictsResolved = 1),
        )
        collector.recordOperation(
            type = SyncEventType.PULL,
            durationMillis = 5,
            result = SyncResult.Success(pulled = 1, conflictsResolved = 0),
        )

        val snapshot = collector.snapshot.value

        assertEquals(2, snapshot.pullOperationsSampled)
        assertEquals(0.5, snapshot.conflictRate)
    }

    @Test
    fun recordOutboxDepth_tracksPeak() = runTest {
        val collector = SyncMetricsCollector()

        collector.recordOutboxDepth(3)
        collector.recordOutboxDepth(1)

        assertEquals(3, collector.snapshot.value.maxOutboxDepth)
    }

    @Test
    fun conflictRate_nullUntilPullSampled() = runTest {
        val collector = SyncMetricsCollector()

        collector.recordOperation(SyncEventType.PUSH, durationMillis = 1, result = SyncResult.Success())

        assertNull(collector.snapshot.value.conflictRate)
    }
}