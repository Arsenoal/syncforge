package dev.syncforge.network

import dev.syncforge.api.ExperimentalSyncForgeApi
import io.ktor.client.HttpClient

/**
 * Optional browser entry when depending on `:syncforge-network-ktor` directly.
 *
 * On Kotlin/JS, [createWebKtorSyncTransport] in `:syncforge` is equivalent and does not require
 * this module.
 */
@ExperimentalSyncForgeApi
fun createBrowserKtorSyncTransport(
    baseUrl: String,
    auth: SyncAuthProvider? = null,
    httpClient: HttpClient? = null,
): SyncTransport = when (httpClient) {
    null -> KtorSyncTransport(baseUrl, auth)
    else -> KtorSyncTransport(baseUrl, auth, httpClient)
}