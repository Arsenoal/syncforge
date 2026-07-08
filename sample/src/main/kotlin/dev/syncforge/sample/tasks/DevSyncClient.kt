package dev.syncforge.sample.tasks

import dev.syncforge.entity.SyncedEntity
import dev.syncforge.sample.BuildConfig
import dev.syncforge.sample.notes.NoteEntity
import dev.syncforge.sample.tags.TagEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

object DevSyncClient {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun simulateServerDelete(task: TaskEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = json.encodeToString(
                    SimulateDeleteRequest(
                        entityType = TaskEntity.ENTITY_TYPE,
                        entityId = task.id,
                    ),
                )
                postDevEndpointRaw("/dev/simulate-delete", body)
            }
        }

    suspend fun simulateServerEdit(task: TaskEntity, newTitle: String): Result<Unit> =
        simulateServerEdit(
            entityType = TaskEntity.ENTITY_TYPE,
            entityId = task.id,
            payloadJson = json.encodeToString(
                task.copy(
                    title = newTitle,
                    updatedAtMillis = serverEditTimestamp(task),
                    syncState = dev.syncforge.model.SyncState.SYNCED,
                ),
            ),
        ).map { }

    suspend fun simulateServerEdit(note: NoteEntity, newBody: String): Result<Long> =
        simulateServerEdit(
            entityType = NoteEntity.ENTITY_TYPE,
            entityId = note.id,
            payloadJson = json.encodeToString(
                note.copy(
                    body = newBody,
                    updatedAtMillis = serverEditTimestamp(note),
                    syncState = dev.syncforge.model.SyncState.SYNCED,
                ),
            ),
        )

    suspend fun simulateServerEdit(tag: TagEntity, newLabel: String): Result<Long> =
        simulateServerEdit(
            entityType = TagEntity.ENTITY_TYPE,
            entityId = tag.id,
            payloadJson = json.encodeToString(
                tag.copy(
                    label = newLabel,
                    updatedAtMillis = serverEditTimestamp(tag),
                    syncState = dev.syncforge.model.SyncState.SYNCED,
                ),
            ),
        )

    /**
     * Newer than the local row for LWW and than the host clock so pull deltas are not skipped
     * when the emulator clock lags the mock-server.
     */
    private fun serverEditTimestamp(entity: SyncedEntity): Long =
        maxOf(System.currentTimeMillis(), entity.updatedAtMillis + 1L)

    suspend fun simulateServerEdit(
        entityType: String,
        entityId: String,
        payloadJson: String,
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val body = json.encodeToString(
                SimulateEditRequest(
                    entityType = entityType,
                    entityId = entityId,
                    payloadJson = payloadJson,
                ),
            )
            postDevEndpoint("/dev/simulate-edit", body)
                .let { response ->
                    response.updatedAtMillis
                        ?: error("simulate-edit succeeded but omitted updatedAtMillis")
                }
        }
    }

    private fun postDevEndpointRaw(path: String, body: String) {
        val connection = openPostConnection(path)
        connection.outputStream.use { it.write(body.toByteArray()) }
        val code = connection.responseCode
        if (code !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.readText()
            error("Server returned $code: $errorBody")
        }
    }

    private fun postDevEndpoint(path: String, body: String): SimulateEditResponse {
        val connection = openPostConnection(path)
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
        return json.decodeFromString(responseBody ?: error("Empty simulate-edit response"))
    }

    private fun openPostConnection(path: String): HttpURLConnection =
        (URL("${BuildConfig.SYNC_BASE_URL}$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 5_000
            readTimeout = 5_000
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

    @Serializable
    private data class SimulateDeleteRequest(
        val entityType: String,
        val entityId: String,
    )
}