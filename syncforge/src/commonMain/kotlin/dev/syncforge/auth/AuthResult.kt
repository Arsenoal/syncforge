package dev.syncforge.auth

sealed class AuthResult {
    data class Success(val session: Session) : AuthResult()
    data class Failure(val error: AuthError) : AuthResult()
}

data class Session(
    val expiresAtMillis: Long?,
    val hasRefreshToken: Boolean,
)

data class AuthError(
    val code: Code,
    val message: String,
    val httpStatus: Int? = null,
) {
    enum class Code {
        NETWORK,
        VALIDATION,
        AUTH,
        SERVER,
        UNKNOWN,
    }
}