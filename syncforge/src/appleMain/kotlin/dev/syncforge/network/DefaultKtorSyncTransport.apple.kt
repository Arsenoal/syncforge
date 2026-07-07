package dev.syncforge.network

import io.ktor.client.HttpClient

internal actual fun createKtorSyncTransport(
    baseUrl: String,
    auth: SyncAuthProvider?,
    httpClient: HttpClient?,
): SyncTransport =
    SyncForgeNetworkKtorRuntime.createKtorSyncTransport(baseUrl, auth, httpClient)
        ?: throw IllegalStateException(
            "Default Ktor REST transport is not linked on this target. " +
                "Add studio.syncforge:syncforge-network-ktor and call " +
                "ensureSyncForgeNetworkKtorLoaded() at app startup, or set " +
                "httpClient(appHttpClient) / transport(KtorSyncTransport(baseUrl, auth)) in SyncForge.ios { }.",
        )