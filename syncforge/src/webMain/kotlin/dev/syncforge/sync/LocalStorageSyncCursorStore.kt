package dev.syncforge.sync

import kotlinx.browser.localStorage

/**
 * Persists the pull cursor in [localStorage] (1.6-01 web default; IndexedDB fallback in 1.6-02).
 */
internal class LocalStorageSyncCursorStore(
    private val storageKey: String = DEFAULT_KEY,
) : SyncCursorStore {

    override fun get(): Long =
        localStorage.getItem(storageKey)?.toLongOrNull() ?: 0L

    override fun set(timestampMillis: Long) {
        localStorage.setItem(storageKey, timestampMillis.toString())
    }

    companion object {
        const val DEFAULT_KEY: String = "dev.syncforge.last_sync_cursor_millis"
    }
}