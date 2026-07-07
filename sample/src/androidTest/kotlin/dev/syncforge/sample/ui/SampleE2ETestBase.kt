package dev.syncforge.sample.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import dev.syncforge.sample.MainActivity
import dev.syncforge.sample.SampleApplication
import org.junit.Assume
import org.junit.Rule
import java.net.HttpURLConnection
import java.net.URL

/**
 * Shared Compose UI Test helpers for [:sample] E2E tests against [:mock-server] on the host
 * (10.0.2.2:8080 from the emulator).
 *
 * Uses [createAndroidComposeRule] instead of UiAutomator — Compose buttons and navigation items
 * are not consistently exposed to the accessibility tree that UiAutomator queries.
 */
abstract class SampleE2ETestBase {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    protected fun prepareDevice() {
        Assume.assumeTrue(
            "Mock server must be running on host port 8080 (./gradlew androidE2e)",
            isMockServerHealthy(),
        )
        resetE2eState()
        waitForAppReady()
    }

    private fun resetE2eState() {
        resetMockServer()
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .applicationContext as SampleApplication
        app.resetForE2eTests()
        composeTestRule.waitForIdle()
    }

    private fun resetMockServer() {
        val baseUrl = InstrumentationRegistry.getArguments().getString("mockServerUrl")
            ?: "http://10.0.2.2:8080"
        runCatching {
            val url = URL("$baseUrl/dev/reset")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2_000
                readTimeout = 2_000
                requestMethod = "POST"
                doOutput = true
            }
            connection.outputStream.use { }
            connection.responseCode
        }
    }

    private fun waitForAppReady() {
        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            composeTestRule.onAllNodesWithTag("sync_button").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithText("New task").fetchSemanticsNodes().isNotEmpty()
        }
    }

    protected fun navigateToTasks() {
        composeTestRule.onNodeWithTag("nav_tasks").performClick()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithText("New task").fetchSemanticsNodes().isNotEmpty()
        }
    }

    protected fun navigateToNotes() {
        composeTestRule.onNodeWithTag("nav_notes").performClick()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithTag("add_note_button").fetchSemanticsNodes().isNotEmpty()
        }
    }

    protected fun navigateToTags() {
        composeTestRule.onNodeWithTag("nav_tags").performClick()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithTag("add_tag_button").fetchSemanticsNodes().isNotEmpty()
        }
    }

    protected fun addTask(title: String) {
        navigateToTasks()
        composeTestRule.onNodeWithTag("new_task_input").performClick()
        composeTestRule.onNodeWithTag("new_task_input").performTextInput(title)
        composeTestRule.onNodeWithTag("add_task_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
    }

    protected fun addNote(title: String, body: String = "", tagLabel: String? = null) {
        navigateToNotes()
        if (tagLabel != null) {
            composeTestRule.onNodeWithTag("note_tag_dropdown").performClick()
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithText(tagLabel).fetchSemanticsNodes().isNotEmpty()
            }
            tapText(tagLabel)
        }
        composeTestRule.onNodeWithTag("new_note_title_input").performClick()
        composeTestRule.onNodeWithTag("new_note_title_input").performTextInput(title)
        if (body.isNotBlank()) {
            composeTestRule.onNodeWithTag("new_note_body_input").performClick()
            composeTestRule.onNodeWithTag("new_note_body_input").performTextInput(body)
        }
        composeTestRule.onNodeWithTag("add_note_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
    }

    protected fun addTag(label: String) {
        navigateToTags()
        composeTestRule.onNodeWithTag("new_tag_input").performClick()
        composeTestRule.onNodeWithTag("new_tag_input").performTextInput(label)
        composeTestRule.onNodeWithTag("add_tag_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
    }

    protected fun syncAndWaitForIdle() {
        tapText("Sync")
        waitForSyncToFinish()
    }

    protected fun tapServerEdit() {
        tapText("Server edit")
    }

    protected fun tapServerDelete() {
        tapText("Server delete")
    }

    protected fun clearLocalDatabase() {
        composeTestRule.onNodeWithTag("demo_clear_local_db").performClick()
        composeTestRule.waitForIdle()
    }

    protected fun resolveConflictKeepLocal() {
        openConflictSheet()
        tapTag("conflict_keep_local")
        composeTestRule.waitForIdle()
    }

    protected fun resolveConflictAcceptRemote() {
        openConflictSheet()
        tapTag("conflict_accept_remote")
        composeTestRule.waitForIdle()
    }

    private fun openConflictSheet() {
        tapText("Resolve")
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithTag("conflict_accept_remote")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    protected fun tapTag(tag: String) {
        composeTestRule.onNodeWithTag(tag).performClick()
    }

    protected fun tapText(text: String) {
        when (text) {
            "Sync" -> composeTestRule.onNodeWithTag("sync_button").performClick()
            else -> {
                val matcher = hasText(text, substring = false, ignoreCase = false) and hasClickAction()
                composeTestRule.waitUntil(timeoutMillis = 15_000) {
                    composeTestRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
                }
                composeTestRule.onAllNodes(matcher).onFirst().scrollToIfPossible().performClick()
            }
        }
    }

    protected fun toggleCheckboxForTask(taskTitle: String) {
        composeTestRule
            .onNodeWithTag("task_checkbox_$taskTitle")
            .scrollToIfPossible()
            .performClick()
    }

    /**
     * Scrolls only when the node sits inside a scrollable container (e.g. off-screen LazyColumn
     * item). No-op for top-bar actions, bottom sheets, and other non-scroll parents.
     */
    private fun SemanticsNodeInteraction.scrollToIfPossible(): SemanticsNodeInteraction = apply {
        try {
            performScrollTo()
        } catch (error: AssertionError) {
            if (!error.message.orEmpty().contains("Scroll SemanticsAction")) {
                throw error
            }
        }
    }

    protected fun waitForSyncToFinish(timeoutMillis: Long = 30_000) {
        composeTestRule.waitUntil(timeoutMillis) {
            composeTestRule.onAllNodesWithText("Syncing", substring = true)
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    protected fun waitForAnyText(vararg options: String, timeoutMillis: Long = 30_000) {
        composeTestRule.waitUntil(timeoutMillis) {
            options.any { option ->
                composeTestRule.onAllNodesWithText(option, substring = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        }
    }

    protected fun waitForTextContains(text: String, timeoutMillis: Long = 15_000) {
        composeTestRule.waitUntil(timeoutMillis) {
            composeTestRule.onAllNodesWithText(text, substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    protected fun waitForTextGone(text: String, timeoutMillis: Long = 15_000) {
        composeTestRule.waitUntil(timeoutMillis) {
            composeTestRule.onAllNodesWithText(text, substring = true)
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    protected fun waitForTaskRemoved(taskTitle: String, timeoutMillis: Long = 30_000) {
        composeTestRule.waitUntil(timeoutMillis) {
            composeTestRule.onAllNodesWithTag("task_checkbox_$taskTitle")
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    protected fun waitForRowSyncState(itemTitle: String, stateLabel: String, timeoutMillis: Long = 30_000) {
        composeTestRule.waitUntil(timeoutMillis) {
            val hasTitle = composeTestRule.onAllNodesWithText(itemTitle)
                .fetchSemanticsNodes()
                .isNotEmpty()
            val hasState = composeTestRule.onAllNodesWithText(stateLabel, substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
            hasTitle && hasState
        }
    }

    protected fun uniqueTitle(prefix: String): String =
        "$prefix ${System.currentTimeMillis()}"

    private fun isMockServerHealthy(): Boolean =
        runCatching {
            val url = URL(
                InstrumentationRegistry.getArguments().getString("mockServerUrl")
                    ?: "http://10.0.2.2:8080/health",
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2_000
                readTimeout = 2_000
                requestMethod = "GET"
            }
            connection.responseCode == 200
        }.getOrDefault(false)
}