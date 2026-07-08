package dev.syncforge.sample.desktop

import dev.syncforge.entity.SyncedEntity
import dev.syncforge.model.SyncState
import dev.syncforge.sample.ios.SampleNoteEntity
import dev.syncforge.sample.ios.SampleTaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/** Dev-only mock-server helpers (same endpoints as Android :sample [DevSyncClient]). */
object MockServerDevClient {

    private val json = Json { ignoreUnknownKeys = true }

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
    ): Long = withContext(Dispatchers.IO) {
        val body = json.encodeToString(
            SimulateEditRequest(
                entityType = entityType,
                entityId = entityId,
                payloadJson = payloadJson,
            ),
        )
        postSimulateEdit(baseUrl, body)
    }

    private fun serverEditTimestamp(entity: SyncedEntity): Long =
        maxOf(System.currentTimeMillis(), entity.updatedAtMillis + 1L)

    private fun postSimulateEdit(baseUrl: String, body: String): Long {
        val connection = (URL("$baseUrl/dev/simulate-edit").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 5_000
            readTimeout = 5_000
        }
        connection.outputStream.use { it.write(body.toByteArray()) }
        val code = connection.responseCode
        val responseBody = if (code in 200..299) {
            connection.inputStream.bufferedReader().readText()
        } else {
            connection.errorStream?.bufferedReader()?.readText()
        }
        if (code !in 200..299) {
            error("Server returned $code: $responseBody")
        }
        val response = json.decodeFromString<SimulateEditResponse>(responseBody ?: error("Empty response"))
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