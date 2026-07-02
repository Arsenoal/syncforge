package dev.syncforge.server.auth

import dev.syncforge.server.installSyncServerPlugins
import dev.syncforge.server.syncRoutes
import dev.syncforge.server.InMemorySyncStore
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthRoutesTest {

    @Test
    fun login_thenAccessProtectedSyncRoute() = testApplication {
        val authStore = InMemoryAuthStore()
        val syncStore = InMemorySyncStore()
        application {
            installSyncServerPlugins()
            installSyncBearerAuth(authStore)
            routing {
                authRoutes(authStore)
                authenticate("syncforge-bearer") {
                    syncRoutes(syncStore, includeHealth = false)
                }
            }
        }

        authStore.register("user@example.com", "password")

        val loginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"user@example.com","password":"password"}""")
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)
        val body = loginResponse.bodyAsText()
        assertTrue(body.contains("access_token"))

        val token = Regex(""""access_token"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
        requireNotNull(token)

        val unauthorized = client.get("/sync/pull?since=0")
        assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)

        val authorized = client.get("/sync/pull?since=0") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, authorized.status)
    }
}