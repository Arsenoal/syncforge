package dev.syncforge.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class TokenResponseParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    fun parse(body: String, mapping: AuthTokenFieldMapping): ParsedTokens {
        val root = json.parseToJsonElement(body).jsonObject
        val access = root.requireString(mapping.accessToken)
        val refresh = root.optionalString(mapping.refreshToken)
        val expiresIn = root.optionalLong(mapping.expiresInSeconds)
        val expiresAt = expiresIn?.let { dev.syncforge.sync.currentTimeMillis() + it * 1_000 }
        return ParsedTokens(
            accessToken = access,
            refreshToken = refresh,
            expiresAtMillis = expiresAt,
        )
    }
}

internal data class ParsedTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMillis: Long?,
)

private fun JsonObject.requireString(key: String): String =
    this[key]?.asStringOrNull()
        ?: error("Missing required auth field: $key")

private fun JsonObject.optionalString(key: String): String? =
    this[key]?.asStringOrNull()

private fun JsonObject.optionalLong(key: String): Long? =
    this[key]?.asLongOrNull()

private fun JsonElement.asStringOrNull(): String? = when (this) {
    is JsonPrimitive -> content.takeIf { it.isNotBlank() }
    else -> null
}

private fun JsonElement.asLongOrNull(): Long? = when (this) {
    is JsonPrimitive -> content.toLongOrNull()
    else -> null
}