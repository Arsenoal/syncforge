package dev.syncforge.sync

import java.io.File

object SyncCursorStoreFactory {

    fun create(
        directory: File = defaultDirectory(),
        fileName: String = "syncforge_cursor.properties",
        key: String = FileSyncCursorStore.DEFAULT_KEY,
    ): SyncCursorStore = FileSyncCursorStore(
        file = File(directory, fileName),
        key = key,
    )

    fun defaultDirectory(): File =
        File(System.getProperty("user.home"), ".syncforge")
}