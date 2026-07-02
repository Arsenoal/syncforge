package dev.syncforge.sync

import android.content.Context

class SharedPreferencesSyncCursorStore(
    context: Context,
    prefsName: String = "syncforge_sync_cursor",
) : SyncCursorStore {

    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override fun get(): Long = prefs.getLong(KEY_LAST_SYNC_CURSOR, 0L)

    override fun set(timestampMillis: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_CURSOR, timestampMillis).apply()
    }

    private companion object {
        const val KEY_LAST_SYNC_CURSOR = "last_sync_cursor_millis"
    }
}

object SyncCursorStoreFactory {
    fun create(context: Context): SyncCursorStore = SharedPreferencesSyncCursorStore(context)
}