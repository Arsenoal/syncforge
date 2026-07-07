package dev.syncforge.network

import io.ktor.client.HttpClient

/**
 * Default REST transport when [studio.syncforge:syncforge-network-ktor] is on the classpath.
 * Pass a non-null [httpClient] to reuse an app-owned Ktor client; otherwise the bundled platform
 * client is used when the adapter module is linked.
 */
internal expect fun createKtorSyncTransport(
    baseUrl: String,
    auth: SyncAuthProvider?,
    httpClient: HttpClient?,
): SyncTransport

internal fun createDefaultKtorSyncTransport(
    baseUrl: String,
    auth: SyncAuthProvider?,
): SyncTransport = createKtorSyncTransport(baseUrl, auth, httpClient = null)