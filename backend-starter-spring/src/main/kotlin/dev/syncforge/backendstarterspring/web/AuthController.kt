package dev.syncforge.backendstarterspring.web

import dev.syncforge.server.auth.InMemoryAuthStore
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authStore: InMemoryAuthStore,
) {
    @PostMapping("/register")
    fun register(@RequestBody body: Map<String, String>): ResponseEntity<Any> {
        val email = body.string("email")
            ?: return badRequest("email is required")
        val password = body.string("password")
            ?: return badRequest("password is required")
        val tokens = runCatching { authStore.register(email, password) }
            .getOrElse { return conflict(it.message ?: "register failed") }
        return ResponseEntity.ok(tokens.toTokenResponse())
    }

    @PostMapping("/login")
    fun login(@RequestBody body: Map<String, String>): ResponseEntity<Any> {
        val email = body.string("email")
            ?: return badRequest("email is required")
        val password = body.string("password")
            ?: return badRequest("password is required")
        val tokens = runCatching { authStore.login(email, password) }
            .getOrElse { return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody(it.message ?: "login failed")) }
        return ResponseEntity.ok(tokens.toTokenResponse())
    }

    @PostMapping("/refresh")
    fun refresh(@RequestBody body: Map<String, String>): ResponseEntity<Any> {
        val refreshToken = body.string("refresh_token")
            ?: return badRequest("refresh_token is required")
        val tokens = runCatching { authStore.refresh(refreshToken) }
            .getOrElse { return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody(it.message ?: "refresh failed")) }
        return ResponseEntity.ok(tokens.toTokenResponse())
    }

    private fun badRequest(message: String): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(message))

    private fun conflict(message: String): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(message))

    private fun errorBody(message: String): Map<String, String> = mapOf("error" to message)

    private fun Map<String, String>.string(key: String): String? = this[key]?.takeIf { it.isNotBlank() }

}