package dev.syncforge.sync

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import java.io.File

class FileSyncCursorStoreTest {

    @Test
    fun set_persistsAcrossInstances() {
        val dir = createTempDirectory("syncforge-cursor").toFile()
        val store = FileSyncCursorStore(file = File(dir, "cursor.properties"))
        store.set(42L)

        val reloaded = FileSyncCursorStore(file = File(dir, "cursor.properties"))
        assertEquals(42L, reloaded.get())
    }
}