package dev.syncforge.sync

import android.content.Context

/**
 * Legacy Android pull cursor backed by SharedPreferences.
 *
 * Prefer [DataStoreSyncCursorStore] (default via [SyncCursorStoreFactory]) for new apps.
 * Kept for explicit overrides and migration tests.
 */
class SharedPreferencesSyncCursorStore(
    context: Context,
    prefsName: String = DEFAULT_PREFS_NAME,
) : SyncCursorStore {

    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override fun get(): Long = prefs.getLong(KEY_LAST_SYNC_CURSOR, 0L)

    override fun set(timestampMillis: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_CURSOR, timestampMillis).apply()
    }

    companion object {
        const val DEFAULT_PREFS_NAME: String = "syncforge_sync_cursor"
        const val KEY_LAST_SYNC_CURSOR: String = "last_sync_cursor_millis"
    }
}

object SyncCursorStoreFactory {
    fun create(context: Context): SyncCursorStore = DataStoreSyncCursorStore(context)
}