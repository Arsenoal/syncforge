package dev.syncforge.sample.tasks

import dev.syncforge.sample.BuildConfig
import dev.syncforge.sample.tasks.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

object DevSyncClient {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun simulateServerEdit(task: TaskEntity, newTitle: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
        val serverTask = task.copy(
            title = newTitle,
            updatedAtMillis = System.currentTimeMillis(),
            syncState = dev.syncforge.model.SyncState.SYNCED,
        )
        val body = json.encodeToString(
            SimulateEditRequest(
                entityType = TaskEntity.ENTITY_TYPE,
                entityId = task.id,
                payloadJson = json.encodeToString(serverTask),
            ),
        )

        val connection = (URL("${BuildConfig.SYNC_BASE_URL}/dev/simulate-edit").openConnection()
            as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 5_000
            readTimeout = 5_000
        }

        connection.outputStream.use { it.write(body.toByteArray()) }
        val code = connection.responseCode
        if (code !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.readText()
            error("Server returned $code: $errorBody")
        }
            }
        }

    @Serializable
    private data class SimulateEditRequest(
        val entityType: String,
        val entityId: String,
        val payloadJson: String,
    )
}