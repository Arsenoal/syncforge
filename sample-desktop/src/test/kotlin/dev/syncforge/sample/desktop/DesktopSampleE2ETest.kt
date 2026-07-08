package dev.syncforge.sample.desktop

import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class DesktopSampleE2ETest {

    @Test
    fun pushAndPullAgainstMockServer() = runTest {
        val baseUrl = DesktopSampleController.resolveBaseUrl()
        assumeTrue("Mock server must be running ($baseUrl/health)", isMockServerHealthy(baseUrl))
        resetMockServer(baseUrl)

        val dataDir = File(System.getProperty("java.io.tmpdir"), "syncforge-desktop-e2e-${System.nanoTime()}")
        val controller = DesktopSampleController(baseUrl = baseUrl, dataDirectory = dataDir)

        val stamp = System.currentTimeMillis()

        val task = controller.addTask("Desktop E2E task $stamp")
        val taskPush = controller.sync()
        assert(taskPush.isSuccessful()) { "Task push failed: $taskPush" }
        val pushedTask = controller.taskById(task.id) ?: error("Task missing after push")
        assert(pushedTask.isSynced()) { "Expected SYNCED task after push, got ${pushedTask.syncState}" }

        val note = controller.addNote("Desktop E2E note $stamp", body = "local body")
        val notePush = controller.sync()
        assert(notePush.isSuccessful()) { "Note push failed: $notePush" }
        val pushedNote = controller.noteById(note.id) ?: error("Note missing after push")
        assert(pushedNote.isSynced()) { "Expected SYNCED note after push, got ${pushedNote.syncState}" }

        val remoteBody = "server body $stamp"
        MockServerDevClient.simulateServerEdit(baseUrl, pushedNote, remoteBody)

        val pullResult = controller.sync()
        assert(pullResult.isSuccessful()) { "Pull failed: $pullResult" }
        val pulledNote = controller.noteById(note.id) ?: error("Note missing after pull")
        assert(pulledNote.body == remoteBody) { "Expected '$remoteBody', got '${pulledNote.body}'" }
        assert(pulledNote.isSynced()) { "Expected SYNCED after pull, got ${pulledNote.syncState}" }
    }

    private fun isMockServerHealthy(baseUrl: String): Boolean =
        runCatching {
            val connection = (URL("$baseUrl/health").openConnection() as HttpURLConnection).apply {
                connectTimeout = 2_000
                readTimeout = 2_000
                requestMethod = "GET"
            }
            connection.connect()
            connection.responseCode in 200..299
        }.getOrDefault(false)

    private fun resetMockServer(baseUrl: String) {
        val connection = (URL("$baseUrl/dev/reset").openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 5_000
            requestMethod = "POST"
            doOutput = true
        }
        connection.outputStream.use { }
        check(connection.responseCode in 200..299) {
            "Mock server reset failed with ${connection.responseCode}"
        }
    }
}