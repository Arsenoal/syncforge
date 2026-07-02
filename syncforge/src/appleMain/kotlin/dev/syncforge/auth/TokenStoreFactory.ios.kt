package dev.syncforge.auth

import platform.Foundation.NSUserDefaults

actual fun createTokenStore(): TokenStore = UserDefaultsTokenStore()

private class UserDefaultsTokenStore : TokenStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun accessToken(): String? = defaults.stringForKey(KEY_ACCESS)
    override fun refreshToken(): String? = defaults.stringForKey(KEY_REFRESH)
    override fun expiresAtMillis(): Long? {
        val value = defaults.doubleForKey(KEY_EXPIRES)
        return if (value > 0.0) value.toLong() else null
    }

    override fun save(accessToken: String, refreshToken: String?, expiresAtMillis: Long?) {
        defaults.setObject(accessToken, KEY_ACCESS)
        if (refreshToken != null) {
            defaults.setObject(refreshToken, KEY_REFRESH)
        } else {
            defaults.removeObjectForKey(KEY_REFRESH)
        }
        if (expiresAtMillis != null) {
            defaults.setDouble(expiresAtMillis.toDouble(), KEY_EXPIRES)
        } else {
            defaults.removeObjectForKey(KEY_EXPIRES)
        }
        defaults.synchronize()
    }

    override fun clear() {
        defaults.removeObjectForKey(KEY_ACCESS)
        defaults.removeObjectForKey(KEY_REFRESH)
        defaults.removeObjectForKey(KEY_EXPIRES)
        defaults.synchronize()
    }

    companion object {
        private const val KEY_ACCESS = "syncforge.auth.access"
        private const val KEY_REFRESH = "syncforge.auth.refresh"
        private const val KEY_EXPIRES = "syncforge.auth.expires"
    }
}