package dev.syncforge.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted token storage (Android 1.1+ default). Migrates legacy plain SharedPreferences on first read.
 */
internal class EncryptedTokenStore(
    context: Context,
    prefsName: String = PREFS_NAME,
    legacyPrefsName: String = PREFS_NAME,
) : TokenStore {

    private val appContext = context.applicationContext
    private val encryptedPrefs = createEncryptedPrefs(appContext, prefsName)
    private val legacyPrefs = appContext.getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE)

    init {
        migrateLegacyIfNeeded()
    }

    override fun accessToken(): String? = encryptedPrefs.getString(KEY_ACCESS, null)

    override fun refreshToken(): String? = encryptedPrefs.getString(KEY_REFRESH, null)

    override fun expiresAtMillis(): Long? =
        encryptedPrefs.getLong(KEY_EXPIRES, -1L).takeIf { it >= 0L }

    override fun save(accessToken: String, refreshToken: String?, expiresAtMillis: Long?) {
        encryptedPrefs.edit()
            .putString(KEY_ACCESS, accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .apply {
                if (expiresAtMillis != null) putLong(KEY_EXPIRES, expiresAtMillis)
                else remove(KEY_EXPIRES)
            }
            .apply()
    }

    override fun clear() {
        encryptedPrefs.edit().clear().apply()
    }

    private fun migrateLegacyIfNeeded() {
        if (encryptedPrefs.contains(KEY_ACCESS)) return
        if (!legacyPrefs.contains(KEY_ACCESS)) return

        save(
            accessToken = legacyPrefs.getString(KEY_ACCESS, null) ?: return,
            refreshToken = legacyPrefs.getString(KEY_REFRESH, null),
            expiresAtMillis = legacyPrefs.getLong(KEY_EXPIRES, -1L).takeIf { it >= 0L },
        )
        legacyPrefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME: String = "syncforge_auth_tokens"
        const val KEY_ACCESS: String = "access_token"
        const val KEY_REFRESH: String = "refresh_token"
        const val KEY_EXPIRES: String = "expires_at_millis"
    }
}

/** Legacy plain-text store — retained for migration tests and explicit overrides. */
internal class LegacySharedPreferencesTokenStore(context: Context) : TokenStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun accessToken(): String? = prefs.getString(KEY_ACCESS, null)
    override fun refreshToken(): String? = prefs.getString(KEY_REFRESH, null)
    override fun expiresAtMillis(): Long? =
        prefs.getLong(KEY_EXPIRES, -1L).takeIf { it >= 0L }

    override fun save(accessToken: String, refreshToken: String?, expiresAtMillis: Long?) {
        prefs.edit()
            .putString(KEY_ACCESS, accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .apply {
                if (expiresAtMillis != null) putLong(KEY_EXPIRES, expiresAtMillis)
                else remove(KEY_EXPIRES)
            }
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = EncryptedTokenStore.PREFS_NAME
        const val KEY_ACCESS = EncryptedTokenStore.KEY_ACCESS
        const val KEY_REFRESH = EncryptedTokenStore.KEY_REFRESH
        const val KEY_EXPIRES = EncryptedTokenStore.KEY_EXPIRES
    }
}

private fun createEncryptedPrefs(context: Context, prefsName: String): SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        context,
        prefsName,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
}