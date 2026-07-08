package dev.syncforge.backendstarterspring.web

import dev.syncforge.server.auth.AuthTokens
import kotlinx.serialization.Serializable

@Serializable
data class TokenResponse(
    val access_token: String,
    val refresh_token: String,
    val expires_in: Long,
)

fun AuthTokens.toTokenResponse(): TokenResponse = TokenResponse(
    access_token = accessToken,
    refresh_token = refreshToken,
    expires_in = expiresInSeconds,
)