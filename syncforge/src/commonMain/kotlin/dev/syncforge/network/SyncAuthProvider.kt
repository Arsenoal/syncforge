package dev.syncforge.network

/**
 * Supplies a bearer token for [KtorSyncTransport] requests.
 */
fun interface SyncAuthProvider {
    fun bearerToken(): String?

    companion object {
        fun bearer(tokenProvider: () -> String?): SyncAuthProvider =
            SyncAuthProvider { tokenProvider() }
    }
}