package dev.syncforge.auth

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncForgeAuthServiceTest {

    @Test
    fun login_persistsTokensAndUpdatesAuthState() = runTest {
        val store = InMemoryTokenStore()
        val service = authServiceWithEngine(
            store = store,
            engine = MockEngine { request ->
                when (request.url.encodedPath) {
                    "/auth/login" -> respond(
                        content = """{"access_token":"tok","refresh_token":"ref","expires_in":3600}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                    else -> respond("Not found", HttpStatusCode.NotFound, jsonHeaders())
                }
            },
        )

        val result = service.login(mapOf("email" to "a@b.com", "password" to "pw"))

        assertTrue(result is AuthResult.Success)
        assertEquals("tok", store.accessToken())
        assertEquals("ref", store.refreshToken())
        assertTrue(service.authState.value is AuthState.LoggedIn)
    }

    private fun authServiceWithEngine(
        store: TokenStore,
        engine: MockEngine,
    ): SyncForgeAuthService = SyncForgeAuthService.create(
        baseUrl = "http://localhost:8080",
        config = BuiltInAuthConfig(),
        tokenStore = store,
        engine = engine,
    )

    private fun jsonHeaders() =
        headersOf(HttpHeaders.ContentType, "application/json")
}