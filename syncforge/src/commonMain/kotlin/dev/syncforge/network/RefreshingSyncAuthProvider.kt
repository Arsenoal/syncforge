package dev.syncforge.network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bearer auth with an automatic refresh hook used by [KtorSyncTransport].
 *
 * On HTTP **401**, the transport calls [refreshAccessToken] once and retries the request.
 * The [accessTokenProvider] should read from the same store that [refresh] updates.
 *
 * ```
 * val store = TokenStore()
 * val auth = RefreshingSyncAuthProvider(
 *     accessTokenProvider = { store.accessToken },
 *     refresh = {
 *         store.accessToken = oauth.refresh(store.refreshToken)
 *         store.accessToken
 *     },
 * )
 * SyncForge.android(context) {
 *     auth(auth)
 * }
 * ```
 */
class RefreshingSyncAuthProvider(
    private val accessTokenProvider: () -> String?,
    private val refresh: suspend () -> String?,
) : SyncAuthProvider {

    private val refreshMutex = Mutex()

    override fun bearerToken(): String? = accessTokenProvider()

    /** Invoked by [KtorSyncTransport] after 401; serialized so parallel syncs share one refresh. */
    suspend fun refreshAccessToken(): String? = refreshMutex.withLock {
        refresh()
    }

    companion object {
        fun create(
            accessTokenProvider: () -> String?,
            refresh: suspend () -> String?,
        ): RefreshingSyncAuthProvider = RefreshingSyncAuthProvider(accessTokenProvider, refresh)
    }
}