package dev.syncforge.network

import dev.syncforge.api.ExperimentalSyncForgeApi
import io.ktor.client.HttpClient

/**
 * Browser REST transport using `ktor-client-js` (fetch).
 *
 * Built into `:syncforge` on Kotlin/JS — **no** `:syncforge-network-ktor` dependency required.
 * Optional `syncforge-network-ktor` still registers [KtorSyncTransport] when on the classpath.
 */
@ExperimentalSyncForgeApi
fun createWebKtorSyncTransport(
    baseUrl: String,
    auth: SyncAuthProvider? = null,
    httpClient: HttpClient? = null,
): SyncTransport = createKtorSyncTransport(baseUrl, auth, httpClient)