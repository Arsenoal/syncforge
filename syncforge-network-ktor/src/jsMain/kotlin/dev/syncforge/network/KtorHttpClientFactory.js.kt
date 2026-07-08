package dev.syncforge.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import kotlinx.serialization.json.Json

internal actual fun createPlatformHttpClient(auth: SyncAuthProvider?, json: Json): HttpClient =
    buildSyncForgeHttpClient(Js.create(), auth, json)