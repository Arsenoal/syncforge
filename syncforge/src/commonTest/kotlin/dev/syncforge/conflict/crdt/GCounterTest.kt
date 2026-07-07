package dev.syncforge.conflict.crdt

import kotlin.test.Test
import kotlin.test.assertEquals

class GCounterTest {

    @Test
    fun increment_accumulatesPerReplica() {
        val counter = GCounter.zero()
            .increment("device-a", delta = 2)
            .increment("device-b", delta = 3)

        assertEquals(5, counter.value())
    }

    @Test
    fun merge_takesPointwiseMaxThenSums() {
        val left = GCounter(mapOf("device-a" to 4, "device-b" to 1))
        val right = GCounter(mapOf("device-a" to 2, "device-c" to 5))

        assertEquals(10, left.merge(right).value())
    }
}