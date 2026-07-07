package dev.syncforge.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TokenStoreTest {

    @Test
    fun encryptedStore_persistsTokens() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = EncryptedTokenStore(context, prefsName = "test_encrypted_tokens")

        store.save(accessToken = "access-1", refreshToken = "refresh-1", expiresAtMillis = 9_999L)
        assertEquals("access-1", store.accessToken())
        assertEquals("refresh-1", store.refreshToken())
        assertEquals(9_999L, store.expiresAtMillis())

        val reloaded = EncryptedTokenStore(context, prefsName = "test_encrypted_tokens")
        assertEquals("access-1", reloaded.accessToken())
    }

    @Test
    fun encryptedStore_migratesLegacySharedPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val legacyName = "test_legacy_auth_tokens"
        val legacyPrefs = context.getSharedPreferences(legacyName, Context.MODE_PRIVATE)
        legacyPrefs.edit()
            .putString(EncryptedTokenStore.KEY_ACCESS, "legacy-access")
            .putString(EncryptedTokenStore.KEY_REFRESH, "legacy-refresh")
            .putLong(EncryptedTokenStore.KEY_EXPIRES, 42L)
            .apply()

        val migrated = EncryptedTokenStore(
            context,
            prefsName = "test_migrated_encrypted_tokens",
            legacyPrefsName = legacyName,
        )
        assertEquals("legacy-access", migrated.accessToken())
        assertEquals("legacy-refresh", migrated.refreshToken())
        assertEquals(42L, migrated.expiresAtMillis())
        assertFalse(legacyPrefs.contains(EncryptedTokenStore.KEY_ACCESS))
    }

    @Test
    fun encryptedStore_clearRemovesTokens() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = EncryptedTokenStore(context, prefsName = "test_clear_tokens")
        store.save(accessToken = "a", refreshToken = "r", expiresAtMillis = 1L)

        store.clear()
        assertNull(store.accessToken())
        assertNull(store.refreshToken())
        assertNull(store.expiresAtMillis())
    }
}