package dev.syncforge.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.Json

internal actual fun createPlatformHttpClient(auth: SyncAuthProvider?, json: Json): HttpClient =
    buildSyncForgeHttpClient(OkHttp.create(), auth, json)