package dev.syncforge.network

import dev.syncforge.api.ExperimentalSyncForgeApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

internal actual fun createKtorSyncTransport(
    baseUrl: String,
    auth: SyncAuthProvider?,
    httpClient: HttpClient?,
): SyncTransport {
    SyncForgeNetworkKtorRuntime.createKtorSyncTransport(baseUrl, auth, httpClient)?.let { return it }
    return createBrowserRestSyncTransport(baseUrl, auth, httpClient)
}

@ExperimentalSyncForgeApi
internal fun createBrowserRestSyncTransport(
    baseUrl: String,
    auth: SyncAuthProvider?,
    httpClient: HttpClient?,
): SyncTransport {
    val client = httpClient ?: buildSyncForgeHttpClient(Js.create(), auth, SyncForgeJson)
    return RestSyncTransport(
        baseUrl = baseUrl,
        httpClient = JsKtorSyncHttpClient(client),
        refreshingAuth = auth as? RefreshingSyncAuthProvider,
    )
}