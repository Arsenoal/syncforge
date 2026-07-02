package dev.syncforge.auth

/**
 * JSON field names when parsing auth responses from your backend.
 *
 * ```
 * auth {
 *     tokenFields(
 *         accessToken = "accessToken",
 *         refreshToken = "refreshToken",
 *         expiresInSeconds = "expiresIn",
 *     )
 * }
 * ```
 */
data class AuthTokenFieldMapping(
    val accessToken: String = "access_token",
    val refreshToken: String = "refresh_token",
    val expiresInSeconds: String = "expires_in",
)