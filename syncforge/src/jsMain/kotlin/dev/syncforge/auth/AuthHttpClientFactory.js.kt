package dev.syncforge.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import kotlinx.serialization.json.Json

internal actual fun createAuthHttpClient(engine: io.ktor.client.engine.HttpClientEngine, json: Json): HttpClient =
    buildAuthHttpClient(engine, json)

internal actual fun createAuthHttpClient(json: Json): HttpClient =
    buildAuthHttpClient(Js.create(), json)