package dev.syncforge.trace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncTraceTest {

    @Test
    fun noOpTracer_isDisabled() {
        assertFalse(SyncTracer.None.isEnabled)
    }

    @Test
    fun runSpan_whenDisabled_doesNotAllocateSpan() {
        var startCount = 0
        val tracer = object : SyncTracer {
            override val isEnabled: Boolean = false
            override fun startSpan(name: SyncSpanName, attributes: Map<String, String>): SyncSpan {
                startCount++
                error("startSpan must not be called when disabled")
            }
        }

        val result = tracer.runSpan(SyncSpanName.PUSH) { 42 }

        assertEquals(42, result)
        assertEquals(0, startCount)
    }

    @Test
    fun runSpan_whenEnabled_recordsSpan() {
        val tracer = RecordingSyncTracer()

        tracer.runSpan(
            name = SyncSpanName.PULL,
            attributes = mapOf(SyncTraceAttributes.OPERATION to "pull"),
        ) { }

        assertEquals(1, tracer.spans.size)
        assertEquals(SyncSpanName.PULL, tracer.spans.single().name)
        assertEquals("pull", tracer.spans.single().attributes[SyncTraceAttributes.OPERATION])
        assertEquals(SyncSpanStatus.OK, tracer.spans.single().status)
    }

    @Test
    fun runSpan_recordsErrorOnException() {
        val tracer = RecordingSyncTracer()

        val thrown = runCatching {
            tracer.runSpan(SyncSpanName.PUSH) { error("boom") }
        }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
        assertEquals(SyncSpanStatus.ERROR, tracer.spans.single().status)
        assertEquals("boom", tracer.spans.single().exception?.message)
    }
}