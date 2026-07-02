package dev.syncforge.auth

/**
 * Persists access and refresh tokens. Platform implementations use secure storage where available.
 */
interface TokenStore {
    fun accessToken(): String?
    fun refreshToken(): String?
    fun expiresAtMillis(): Long?
    fun save(accessToken: String, refreshToken: String?, expiresAtMillis: Long?)
    fun clear()
}

/** Test/dev in-memory store. */
class InMemoryTokenStore : TokenStore {
    private var access: String? = null
    private var refresh: String? = null
    private var expiresAt: Long? = null

    override fun accessToken(): String? = access
    override fun refreshToken(): String? = refresh
    override fun expiresAtMillis(): Long? = expiresAt

    override fun save(accessToken: String, refreshToken: String?, expiresAtMillis: Long?) {
        access = accessToken
        refresh = refreshToken
        expiresAt = expiresAtMillis
    }

    override fun clear() {
        access = null
        refresh = null
        expiresAt = null
    }
}