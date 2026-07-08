package dev.syncforge.backendstarterspring

import dev.syncforge.model.ChangeType
import dev.syncforge.network.api.OutboxEntryDto
import dev.syncforge.network.api.PushRequest
import dev.syncforge.server.syncServerJson
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class SyncRoutesIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun login_thenPushPull_roundTrip() {
        mockMvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"user@example.com","password":"password"}"""
        }.andExpect {
            status { isOk() }
        }

        val loginBody = mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"user@example.com","password":"password"}"""
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        val token = Regex(""""access_token"\s*:\s*"([^"]+)"""").find(loginBody)?.groupValues?.get(1)
        requireNotNull(token)

        mockMvc.get("/sync/pull?since=0")
            .andExpect { status { isUnauthorized() } }

        val pushBody = syncServerJson.encodeToString(
            PushRequest.serializer(),
            PushRequest(
                entries = listOf(
                    OutboxEntryDto(
                        id = 42,
                        entityType = "tasks",
                        entityId = "task-42",
                        changeType = ChangeType.CREATE,
                        payloadJson = """{"title":"Hello"}""",
                        localVersion = 1,
                        createdAtMillis = 1,
                    ),
                ),
            ),
        )

        mockMvc.post("/sync/push") {
            contentType = MediaType.APPLICATION_JSON
            content = pushBody
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
        }

        val pullBody = mockMvc.get("/sync/pull?since=0") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        assertTrue(pullBody.contains("task-42"))
        assertTrue(pullBody.contains("Hello"))
    }
}