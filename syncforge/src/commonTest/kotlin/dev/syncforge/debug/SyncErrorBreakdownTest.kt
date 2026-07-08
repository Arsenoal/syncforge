package dev.syncforge.debug

import dev.syncforge.model.SyncError
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncErrorBreakdownTest {

    @Test
    fun emptyEvents_returnEmptyBreakdown() {
        assertEquals(SyncErrorBreakdown.Empty, emptyList<SyncEvent>().toErrorBreakdown())
    }

    @Test
    fun failedEvents_groupByErrorCode() {
        val events = listOf(
            event(success = false, code = SyncError.Code.NETWORK),
            event(success = false, code = SyncError.Code.NETWORK),
            event(success = false, code = SyncError.Code.AUTH),
            event(success = true, code = null),
        )

        val breakdown = events.toErrorBreakdown()

        assertEquals(3, breakdown.totalFailures)
        assertEquals(2, breakdown.byCode[SyncError.Code.NETWORK])
        assertEquals(1, breakdown.byCode[SyncError.Code.AUTH])
    }

    private fun event(success: Boolean, code: SyncError.Code?): SyncEvent =
        SyncEvent(
            id = 1,
            timestampMillis = 0,
            type = SyncEventType.PUSH,
            success = success,
            summary = "test",
            errorCode = code,
        )
}