package dev.syncforge.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Persists the pull sync cursor in DataStore Preferences (Android 1.1+ default).
 *
 * Migrates the legacy SharedPreferences value on first read when the DataStore key is absent.
 */
class DataStoreSyncCursorStore(
    context: Context,
    dataStoreName: String = DEFAULT_DATASTORE_NAME,
    legacyPrefsName: String = SharedPreferencesSyncCursorStore.DEFAULT_PREFS_NAME,
) : SyncCursorStore {

    private val appContext = context.applicationContext
    private val dataStore = SyncForgeCursorDataStores.get(appContext, dataStoreName)
    private val legacyPrefs = appContext.getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE)

    @Volatile
    private var cached: Long? = null

    override fun get(): Long = cached ?: loadOnce()

    override fun set(timestampMillis: Long) {
        loadOnce()
        cached = timestampMillis
        runBlocking(Dispatchers.IO) {
            dataStore.edit { preferences ->
                preferences[KEY_LAST_SYNC_CURSOR] = timestampMillis
            }
        }
    }

    private fun loadOnce(): Long = synchronized(this) {
        cached?.let { return it }
        val loaded = runBlocking(Dispatchers.IO) { loadWithMigration() }
        cached = loaded
        loaded
    }

    private suspend fun loadWithMigration(): Long {
        val preferences = dataStore.data.first()
        if (preferences.contains(KEY_LAST_SYNC_CURSOR)) {
            return preferences[KEY_LAST_SYNC_CURSOR] ?: 0L
        }

        if (legacyPrefs.contains(SharedPreferencesSyncCursorStore.KEY_LAST_SYNC_CURSOR)) {
            val legacyValue = legacyPrefs.getLong(SharedPreferencesSyncCursorStore.KEY_LAST_SYNC_CURSOR, 0L)
            dataStore.edit { editPreferences ->
                editPreferences[KEY_LAST_SYNC_CURSOR] = legacyValue
            }
            legacyPrefs.edit()
                .remove(SharedPreferencesSyncCursorStore.KEY_LAST_SYNC_CURSOR)
                .apply()
            return legacyValue
        }

        return 0L
    }

    companion object {
        const val DEFAULT_DATASTORE_NAME: String = "syncforge_sync_cursor"
        val KEY_LAST_SYNC_CURSOR = longPreferencesKey(SharedPreferencesSyncCursorStore.KEY_LAST_SYNC_CURSOR)
    }
}

private object SyncForgeCursorDataStores {
    private val cache = mutableMapOf<String, DataStore<Preferences>>()

    fun get(context: Context, name: String): DataStore<Preferences> = synchronized(cache) {
        cache.getOrPut(name) {
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { context.preferencesDataStoreFile(name) },
            )
        }
    }
}