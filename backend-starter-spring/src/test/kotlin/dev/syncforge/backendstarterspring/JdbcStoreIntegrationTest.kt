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
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("jdbc-test")
class JdbcStoreIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun jdbcStore_persistsPushAcrossRequests() {
        mockMvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"jdbc@example.com","password":"password"}"""
        }

        val loginBody = mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"jdbc@example.com","password":"password"}"""
        }.andReturn().response.contentAsString

        val token = Regex(""""access_token"\s*:\s*"([^"]+)"""").find(loginBody)?.groupValues?.get(1)
        requireNotNull(token)

        val pushBody = syncServerJson.encodeToString(
            PushRequest.serializer(),
            PushRequest(
                entries = listOf(
                    OutboxEntryDto(
                        id = 7,
                        entityType = "notes",
                        entityId = "note-7",
                        changeType = ChangeType.CREATE,
                        payloadJson = """{"body":"jdbc persisted"}""",
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

        assertTrue(pullBody.contains("note-7"))
        assertTrue(pullBody.contains("jdbc persisted"))
    }
}