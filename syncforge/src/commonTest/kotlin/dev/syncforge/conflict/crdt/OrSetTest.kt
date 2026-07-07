package dev.syncforge.conflict.crdt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrSetTest {

    @Test
    fun merge_unionsConcurrentAdds() {
        val left = OrSet<String>().add("alpha", tag = "t1")
        val right = OrSet<String>().add("beta", tag = "t2")

        assertEquals(setOf("alpha", "beta"), left.merge(right).elements())
    }

    @Test
    fun remove_tombstonesTags() {
        val original = OrSet<String>().add("alpha", tag = "t1").add("beta", tag = "t2")
        val removed = original.remove("alpha")

        assertFalse(removed.contains("alpha"))
        assertTrue(removed.contains("beta"))
    }

    @Test
    fun fromCollection_createsTaggedElements() {
        val set = OrSet.fromCollection(listOf("a", "b"))

        assertEquals(setOf("a", "b"), set.elements())
    }
}