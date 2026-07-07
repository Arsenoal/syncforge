package dev.syncforge.network

import io.ktor.client.HttpClient

/**
 * Optional bridge to [studio.syncforge:syncforge-network-ktor].
 *
 * The Ktor adapter registers [createTransport] at class-load time. Core stays free of a
 * compile-time dependency on the adapter module; JVM/Android fall back to reflection when needed.
 */
internal object SyncForgeNetworkKtorRuntime {
    var createTransport: ((baseUrl: String, auth: SyncAuthProvider?, httpClient: HttpClient?) -> SyncTransport)? =
        null

    fun createKtorSyncTransport(
        baseUrl: String,
        auth: SyncAuthProvider?,
        httpClient: HttpClient?,
    ): SyncTransport? = createTransport?.invoke(baseUrl, auth, httpClient)
}

/** Called by [studio.syncforge:syncforge-network-ktor] at class-load time. */
fun registerSyncForgeKtorTransportFactory(
    factory: (baseUrl: String, auth: SyncAuthProvider?, httpClient: HttpClient?) -> SyncTransport,
) {
    SyncForgeNetworkKtorRuntime.createTransport = factory
}