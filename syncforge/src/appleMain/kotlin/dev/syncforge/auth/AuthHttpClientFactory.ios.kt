package dev.syncforge.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.serialization.json.Json

internal actual fun createAuthHttpClient(engine: io.ktor.client.engine.HttpClientEngine, json: Json): HttpClient =
    buildAuthHttpClient(engine, json)

internal actual fun createAuthHttpClient(json: Json): HttpClient =
    buildAuthHttpClient(Darwin.create(), json)