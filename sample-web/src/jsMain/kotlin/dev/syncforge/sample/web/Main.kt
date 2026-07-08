package dev.syncforge.sample.web

import dev.syncforge.compose.toUiModel
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLDivElement
import kotlinx.browser.document
import kotlinx.browser.window

/**
 * Browser entry for `:sample-web`.
 *
 * - `?smoke=1` — push + pull smoke against `:mock-server` (same flow as desktop `--smoke`)
 * - otherwise — minimal status page for manual dev
 */
fun main() {
    val baseUrl = WebSampleController.resolveBaseUrl()

    MainScope().launch {
        runCatching {
            when {
                isSmokeMode() -> runSmoke(baseUrl)
                isMockServerHealthy(baseUrl) -> runInteractivePage(baseUrl)
                else -> renderStatus(
                    title = "SyncForge web sample",
                    body = "Mock server not reachable at $baseUrl/health. Start it with: ./gradlew :mock-server:run",
                    isError = true,
                )
            }
        }.onFailure { error ->
            publishSmokeResult("fail: ${error.message ?: error::class.simpleName}")
            if (isSmokeMode()) {
                console.error("web-smoke failed:", error)
            } else {
                renderStatus("SyncForge web sample", error.message ?: "Unknown error", isError = true)
            }
        }
    }
}

private suspend fun runSmoke(baseUrl: String) {
    if (!isMockServerHealthy(baseUrl)) {
        error("Mock server not reachable at $baseUrl/health")
    }

    val controller = WebSampleController.create(baseUrl = baseUrl)
    val stamp = kotlin.js.Date.now().toLong()

    val task = controller.addTask("Web smoke task $stamp")
    val pushResult = controller.sync()
    check(pushResult.isSuccessful()) { "Push failed: $pushResult" }
    val pushedTask = controller.taskById(task.id) ?: error("Task missing after push")
    check(pushedTask.isSynced()) { "Expected SYNCED after push, got ${pushedTask.syncState}" }

    val note = controller.addNote("Web smoke note $stamp", body = "local body")
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

    val message =
        "web-smoke: ok (push + pull, status=${controller.syncManager.status.value.toUiModel().label})"
    console.log(message)
    publishSmokeResult("ok")
    if (isSmokeMode()) {
        renderStatus("SyncForge web smoke", message, isError = false)
    }
}

private suspend fun runInteractivePage(baseUrl: String) {
    renderStatus(
        title = "SyncForge web sample",
        body = """
            Connected to mock-server at $baseUrl.
            Append ?smoke=1 to run push + pull smoke (same as :sample-desktop --smoke).
        """.trimIndent(),
        isError = false,
    )
}

private fun isSmokeMode(): Boolean =
    window.location.search.contains("smoke=1") ||
        (js("globalThis.SMOKE_MODE") as Boolean? == true)

private fun publishSmokeResult(result: String) {
    val captured = result
    js("globalThis.__syncforgeSmokeResult = captured")
}

private suspend fun isMockServerHealthy(baseUrl: String): Boolean =
    runCatching {
        window.fetch("$baseUrl/health").await().ok
    }.getOrDefault(false)

private fun renderStatus(title: String, body: String, isError: Boolean) {
    val root = document.getElementById("root") as? HTMLDivElement ?: return
    val color = if (isError) "#b00020" else "#1b5e20"
    root.innerHTML = """
        <h1 style="font-family: system-ui, sans-serif;">$title</h1>
        <p style="font-family: system-ui, sans-serif; color: $color;">$body</p>
    """.trimIndent()
}