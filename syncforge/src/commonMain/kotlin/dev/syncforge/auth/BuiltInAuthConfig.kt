package dev.syncforge.auth

/**
 * Configuration for SyncForge-managed register/login/refresh against your backend.
 */
data class BuiltInAuthConfig(
    val registerPath: String = "/auth/register",
    val loginPath: String = "/auth/login",
    val refreshPath: String = "/auth/refresh",
    val logoutPath: String? = "/auth/logout",
    val tokenFields: AuthTokenFieldMapping = AuthTokenFieldMapping(),
    /** JSON field name sent in the refresh request body (value = stored refresh token). */
    val refreshRequestField: String = "refresh_token",
    val requireAuthForSync: Boolean = true,
    val syncAfterLogin: Boolean = true,
    val syncAfterRegister: Boolean = true,
    /** JSON field for email/username in [CharArray] credential overloads. */
    val emailField: String = "email",
    /** JSON field for password in [CharArray] credential overloads. */
    val passwordField: String = "password",
)