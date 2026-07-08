package dev.syncforge.entity

import kotlin.test.Test
import kotlin.test.assertEquals

class SyncEntityJsonTest {

    @Test
    fun withLocalVersion_replacesExistingField() {
        val json = """{"id":"1","localVersion":1,"title":"task"}"""
        assertEquals(
            """{"id":"1","localVersion":4,"title":"task"}""",
            SyncEntityJson.withLocalVersion(json, 4L),
        )
    }

    @Test
    fun withLocalVersion_insertsFieldWhenEncodeDefaultsOmittedIt() {
        val json = """{"id":"1","title":"task"}"""
        assertEquals(
            """{"localVersion":4,"id":"1","title":"task"}""",
            SyncEntityJson.withLocalVersion(json, 4L),
        )
    }
}