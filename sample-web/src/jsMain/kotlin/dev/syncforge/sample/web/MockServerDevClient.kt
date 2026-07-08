package dev.syncforge.sample.web

import dev.syncforge.entity.SyncedEntity
import dev.syncforge.model.SyncState
import dev.syncforge.sample.ios.SampleNoteEntity
import dev.syncforge.sample.ios.SampleTaskEntity
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Dev-only mock-server helpers (same endpoints as [:sample-desktop] [MockServerDevClient]). */
object MockServerDevClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun simulateServerEdit(
        baseUrl: String,
        task: SampleTaskEntity,
        newTitle: String,
    ): Long = simulateServerEdit(
        baseUrl = baseUrl,
        entityType = SampleTaskEntity.ENTITY_TYPE,
        entityId = task.id,
        payloadJson = json.encodeToString(
            task.copy(
                title = newTitle,
                updatedAtMillis = serverEditTimestamp(task),
                syncState = SyncState.SYNCED,
            ),
        ),
    )

    suspend fun simulateServerEdit(
        baseUrl: String,
        note: SampleNoteEntity,
        newBody: String,
    ): Long = simulateServerEdit(
        baseUrl = baseUrl,
        entityType = SampleNoteEntity.ENTITY_TYPE,
        entityId = note.id,
        payloadJson = json.encodeToString(
            note.copy(
                body = newBody,
                updatedAtMillis = serverEditTimestamp(note),
                syncState = SyncState.SYNCED,
            ),
        ),
    )

    private suspend fun simulateServerEdit(
        baseUrl: String,
        entityType: String,
        entityId: String,
        payloadJson: String,
    ): Long {
        val body = json.encodeToString(
            SimulateEditRequest(
                entityType = entityType,
                entityId = entityId,
                payloadJson = payloadJson,
            ),
        )
        return postSimulateEdit(baseUrl, body)
    }

    private fun serverEditTimestamp(entity: SyncedEntity): Long =
        maxOf(kotlin.js.Date.now().toLong(), entity.updatedAtMillis + 1L)

    private suspend fun postSimulateEdit(baseUrl: String, body: String): Long {
        val response = httpClient.post("$baseUrl/dev/simulate-edit") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body<SimulateEditResponse>()
        return response.updatedAtMillis ?: error("simulate-edit succeeded but omitted updatedAtMillis")
    }

    @Serializable
    private data class SimulateEditRequest(
        val entityType: String,
        val entityId: String,
        val payloadJson: String,
    )

    @Serializable
    private data class SimulateEditResponse(
        val updated: Boolean,
        val updatedAtMillis: Long? = null,
        val message: String? = null,
    )
}