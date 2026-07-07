package dev.syncforge.network

import io.ktor.client.HttpClient

internal actual fun createKtorSyncTransport(
    baseUrl: String,
    auth: SyncAuthProvider?,
    httpClient: HttpClient?,
): SyncTransport =
    SyncForgeNetworkKtorRuntime.createKtorSyncTransport(baseUrl, auth, httpClient)
        ?: KtorSyncTransportReflection.create(baseUrl, auth, httpClient)