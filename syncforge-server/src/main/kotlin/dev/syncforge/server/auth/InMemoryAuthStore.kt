package dev.syncforge.server.auth

import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long = 3600,
)

data class AuthUser(
    val email: String,
    val passwordHash: String,
)

class InMemoryAuthStore {

    private val users = ConcurrentHashMap<String, AuthUser>()
    private val accessToEmail = ConcurrentHashMap<String, String>()
    private val refreshToEmail = ConcurrentHashMap<String, String>()

    fun register(email: String, password: String): AuthTokens {
        require(email.isNotBlank() && password.isNotBlank()) { "email and password are required" }
        val key = email.trim().lowercase()
        check(users.putIfAbsent(key, AuthUser(key, hash(password))) == null) {
            "User already exists"
        }
        return issueTokens(key)
    }

    fun login(email: String, password: String): AuthTokens {
        val key = email.trim().lowercase()
        val user = users[key] ?: error("Invalid credentials")
        check(user.passwordHash == hash(password)) { "Invalid credentials" }
        return issueTokens(key)
    }

    fun refresh(refreshToken: String): AuthTokens {
        val email = refreshToEmail[refreshToken] ?: error("Invalid refresh token")
        refreshToEmail.remove(refreshToken)
        return issueTokens(email)
    }

    fun validateAccessToken(token: String?): String? {
        if (token.isNullOrBlank()) return null
        return accessToEmail[token]
    }

    private fun issueTokens(email: String): AuthTokens {
        val access = "access-${UUID.randomUUID()}"
        val refresh = "refresh-${UUID.randomUUID()}"
        accessToEmail[access] = email
        refreshToEmail[refresh] = email
        return AuthTokens(accessToken = access, refreshToken = refresh)
    }

    private fun hash(password: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(password.toByteArray())
            .joinToString("") { "%02x".format(it) }
}