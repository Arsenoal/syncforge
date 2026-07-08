package dev.syncforge.sync

object SyncCursorStoreFactory {

    fun create(key: String = LocalStorageSyncCursorStore.DEFAULT_KEY): SyncCursorStore =
        LocalStorageSyncCursorStore(storageKey = key)

    /** Namespaced cursor key — pair with [databaseName][dev.syncforge.WebSyncForgeDsl.databaseName]. */
    fun createForDatabase(databaseName: String): SyncCursorStore =
        create(key = cursorStorageKey(databaseName))

    fun cursorStorageKey(databaseName: String): String =
        "dev.syncforge.cursor.$databaseName"
}