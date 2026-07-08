package dev.syncforge.sync

object SyncCursorStoreFactory {

    fun create(key: String = LocalStorageSyncCursorStore.DEFAULT_KEY): SyncCursorStore =
        LocalStorageSyncCursorStore(storageKey = key)
}