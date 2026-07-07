package dev.syncforge.conflict.crdt

import kotlin.test.Test
import kotlin.test.assertEquals

class LwwRegisterTest {

    @Test
    fun merge_prefersNewerTimestamp() {
        val older = LwwRegister("local", timestamp = 100)
        val newer = LwwRegister("remote", timestamp = 200)

        assertEquals("remote", older.merge(newer).value)
        assertEquals("remote", newer.merge(older).value)
    }

    @Test
    fun merge_usesNodeIdTieBreaker() {
        val left = LwwRegister("left", timestamp = 100, nodeId = "a")
        val right = LwwRegister("right", timestamp = 100, nodeId = "b")

        assertEquals("right", left.merge(right).value)
        assertEquals("right", right.merge(left).value)
    }
}