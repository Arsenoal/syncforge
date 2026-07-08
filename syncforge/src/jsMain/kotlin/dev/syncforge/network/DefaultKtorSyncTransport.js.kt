package dev.syncforge.network

import io.ktor.client.HttpClient

internal actual fun createKtorSyncTransport(
    baseUrl: String,
    auth: SyncAuthProvider?,
    httpClient: HttpClient?,
): SyncTransport {
    SyncForgeNetworkKtorRuntime.createKtorSyncTransport(baseUrl, auth, httpClient)?.let { return it }
    error(
        "syncforge-network-ktor is required for browser transport — " +
            "add `implementation(project(\":syncforge-network-ktor\"))` to your app's jsMain dependencies",
    )
}