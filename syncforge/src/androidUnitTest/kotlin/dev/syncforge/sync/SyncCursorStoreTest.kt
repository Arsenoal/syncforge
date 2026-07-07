package dev.syncforge.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncCursorStoreTest {

    @Test
    fun dataStoreStore_persistsCursor() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = DataStoreSyncCursorStore(
            context,
            dataStoreName = "test_datastore_cursor_persist",
            legacyPrefsName = "test_legacy_prefs_persist",
        )

        store.set(1_234L)
        assertEquals(1_234L, store.get())

        val reloaded = DataStoreSyncCursorStore(
            context,
            dataStoreName = "test_datastore_cursor_persist",
            legacyPrefsName = "test_legacy_prefs_persist",
        )
        assertEquals(1_234L, reloaded.get())
    }

    @Test
    fun factory_create_usesDataStoreImplementation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SyncCursorStoreFactory.create(context)
        store.set(99L)
        assertEquals(99L, store.get())
        assertEquals(DataStoreSyncCursorStore::class.java, store.javaClass)
    }

    @Test
    fun dataStoreStore_migratesLegacySharedPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val legacyPrefsName = "test_legacy_cursor_migration"
        val dataStoreName = "test_datastore_cursor_migration"

        SharedPreferencesSyncCursorStore(context, prefsName = legacyPrefsName).set(9_876L)

        val migrated = DataStoreSyncCursorStore(
            context,
            dataStoreName = dataStoreName,
            legacyPrefsName = legacyPrefsName,
        )
        assertEquals(9_876L, migrated.get())

        val legacyPrefs = context.getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE)
        assertFalse(legacyPrefs.contains(SharedPreferencesSyncCursorStore.KEY_LAST_SYNC_CURSOR))

        val reloaded = DataStoreSyncCursorStore(
            context,
            dataStoreName = dataStoreName,
            legacyPrefsName = legacyPrefsName,
        )
        assertEquals(9_876L, reloaded.get())
    }

    @Test
    fun sharedPreferencesStore_persistsCursor() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SharedPreferencesSyncCursorStore(context, prefsName = "test_shared_prefs_cursor")

        store.set(1_234L)
        assertEquals(1_234L, store.get())

        val reloaded = SharedPreferencesSyncCursorStore(context, prefsName = "test_shared_prefs_cursor")
        assertEquals(1_234L, reloaded.get())
    }
}