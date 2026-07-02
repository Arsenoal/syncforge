package dev.syncforge.auth

import android.content.Context

private object AndroidTokenStoreContext {
    var appContext: Context? = null
}

/** Called from [dev.syncforge.SyncForge.android] before auth service creation. */
internal fun initTokenStoreContext(context: Context) {
    AndroidTokenStoreContext.appContext = context.applicationContext
}

actual fun createTokenStore(): TokenStore {
    val context = AndroidTokenStoreContext.appContext
        ?: error("TokenStore context not initialized — use SyncForge.android(context)")
    return SharedPreferencesTokenStore(context)
}

private class SharedPreferencesTokenStore(context: Context) : TokenStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    companion object {
        private const val PREFS_NAME = "syncforge_auth_tokens"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_EXPIRES = "expires_at_millis"
    }
}