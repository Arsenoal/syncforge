package dev.syncforge.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncCursorStoreTest {

    @Test
    fun sharedPreferencesStore_persistsCursor() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SharedPreferencesSyncCursorStore(context, prefsName = "test_sync_cursor")

        store.set(1_234L)
        assertEquals(1_234L, store.get())

        val reloaded = SharedPreferencesSyncCursorStore(context, prefsName = "test_sync_cursor")
        assertEquals(1_234L, reloaded.get())
    }
}