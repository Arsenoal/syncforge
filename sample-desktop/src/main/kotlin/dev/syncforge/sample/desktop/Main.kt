package dev.syncforge.sample.desktop

import dev.syncforge.compose.toUiModel
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

fun main(args: Array<String>) {
    val parsed = parseArgs(args)
    if (!isMockServerHealthy(parsed.baseUrl)) {
        System.err.println("Mock server not reachable at ${parsed.baseUrl}/health")
        System.err.println("Start it with: ./gradlew :mock-server:run")
        kotlin.system.exitProcess(1)
    }

    val parentDir = parsed.dataDirectory?.let { File(it) } ?: DesktopSampleController.defaultDataDirectory()
    val dataDir = File(parentDir, "cli-${System.nanoTime()}")
    val controller = DesktopSampleController(baseUrl = parsed.baseUrl, dataDirectory = dataDir)

    runBlocking {
        when {
            parsed.smoke -> runSmoke(controller, parsed.baseUrl)
            parsed.taskTitle != null -> runSingleTaskDemo(controller, parsed.taskTitle)
            else -> runInteractiveHelp(parsed.baseUrl)
        }
    }
}

private suspend fun runSmoke(controller: DesktopSampleController, baseUrl: String) {
    val stamp = System.currentTimeMillis()
    val task = controller.addTask("Desktop smoke task $stamp")
    val pushResult = controller.sync()
    check(pushResult.isSuccessful()) { "Push failed: $pushResult" }
    val pushedTask = controller.taskById(task.id) ?: error("Task missing after push")
    check(pushedTask.isSynced()) { "Expected SYNCED after push, got ${pushedTask.syncState}" }

    val note = controller.addNote("Desktop smoke note $stamp", body = "local body")
    val notePushResult = controller.sync()
    check(notePushResult.isSuccessful()) { "Note push failed: $notePushResult" }
    val pushedNote = controller.noteById(note.id) ?: error("Note missing after push")
    check(pushedNote.isSynced()) { "Expected SYNCED note after push, got ${pushedNote.syncState}" }

    val remoteBody = "server body $stamp"
    MockServerDevClient.simulateServerEdit(baseUrl, pushedNote, remoteBody)
    val pullResult = controller.sync()
    check(pullResult.isSuccessful()) { "Pull failed: $pullResult" }
    val pulledNote = controller.noteById(note.id) ?: error("Note missing after pull")
    check(pulledNote.body == remoteBody) { "Expected body '$remoteBody', got '${pulledNote.body}'" }
    check(pulledNote.isSynced()) { "Expected SYNCED after pull, got ${pulledNote.syncState}" }

    println("desktop-smoke: ok (push + pull, status=${controller.syncManager.status.value.toUiModel().label})")
}

private suspend fun runSingleTaskDemo(controller: DesktopSampleController, title: String) {
    controller.addTask(title)
    val result = controller.sync()
    println("sync: $result (${controller.syncManager.status.value.toUiModel().label})")
    controller.tasks().forEach { task ->
        println("  - ${task.title} [${task.syncState}]")
    }
}

private fun runInteractiveHelp(baseUrl: String) {
    println(
        """
        SyncForge desktop sample (SyncForge.desktop)
        Base URL: $baseUrl

        Usage:
          ./gradlew :sample-desktop:run --args="--smoke"
          ./gradlew :sample-desktop:run --args="--task \"My task\""

        Start mock-server first:
          ./gradlew :mock-server:run
        """.trimIndent(),
    )
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

private data class CliArgs(
    val baseUrl: String,
    val smoke: Boolean,
    val taskTitle: String?,
    val dataDirectory: String?,
)

private fun parseArgs(args: Array<String>): CliArgs {
    var baseUrl = DesktopSampleController.resolveBaseUrl()
    var smoke = false
    var taskTitle: String? = null
    var dataDirectory: String? = null

    var index = 0
    while (index < args.size) {
        when (val arg = args[index]) {
            "--smoke" -> smoke = true
            "--base-url" -> {
                baseUrl = args.getOrNull(index + 1) ?: error("--base-url requires a value")
                index++
            }
            "--task" -> {
                taskTitle = args.getOrNull(index + 1) ?: error("--task requires a value")
                index++
            }
            "--data-dir" -> {
                dataDirectory = args.getOrNull(index + 1) ?: error("--data-dir requires a value")
                index++
            }
            else -> error("Unknown argument: $arg")
        }
        index++
    }
    return CliArgs(baseUrl = baseUrl, smoke = smoke, taskTitle = taskTitle, dataDirectory = dataDirectory)
}