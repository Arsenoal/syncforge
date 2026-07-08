package dev.syncforge.debug

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncLatencyPercentilesTest {

    @Test
    fun emptySamples_returnEmptyPercentiles() {
        val percentiles = emptyList<Long>().toLatencyPercentiles()

        assertEquals(SyncLatencyPercentiles.Empty, percentiles)
    }

    @Test
    fun singleSample_usesSameValueForAllPercentiles() {
        val percentiles = listOf(42L).toLatencyPercentiles()

        assertEquals(42L, percentiles.p50Millis)
        assertEquals(42L, percentiles.p95Millis)
        assertEquals(42L, percentiles.p99Millis)
        assertEquals(1, percentiles.sampleCount)
    }

    @Test
    fun sortedSamples_computeNearestRankPercentiles() {
        val percentiles = (1L..100L).toList().toLatencyPercentiles()

        assertEquals(50L, percentiles.p50Millis)
        assertEquals(95L, percentiles.p95Millis)
        assertEquals(99L, percentiles.p99Millis)
        assertEquals(100, percentiles.sampleCount)
    }

    @Test
    fun emptyCompanion_hasNullPercentiles() {
        assertNull(SyncLatencyPercentiles.Empty.p50Millis)
        assertEquals(0, SyncLatencyPercentiles.Empty.sampleCount)
    }
}