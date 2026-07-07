package dev.syncforge.network

import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json

/**
 * Creates a platform [HttpClient] with the SyncForge default configuration.
 *
 * Android uses OkHttp; iOS uses Darwin. Tests may inject [MockEngine] via [KtorSyncTransport.createForTest].
 */
internal expect fun createPlatformHttpClient(auth: SyncAuthProvider?, json: Json): HttpClient