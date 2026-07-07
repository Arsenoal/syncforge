package dev.syncforge.sample.ui

import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task-focused E2E tests against [:mock-server] on the host (10.0.2.2:8080 from the emulator).
 */
@RunWith(AndroidJUnit4::class)
class TasksE2ETest : SampleE2ETestBase() {

    @Before
    fun setUp() {
        prepareDevice()
    }

    @Test
    fun addTask_sync_showsSyncedStatus() {
        val taskTitle = uniqueTitle("E2E Sync")
        addTask(taskTitle)
        tapText("Sync")
        waitForSyncToFinish()
        waitForRowSyncState(taskTitle, "Synced", timeoutMillis = 45_000)
    }

    @Test
    fun conflictFlow_serverEditThenLocalEdit_allowsResolution() {
        val taskTitle = uniqueTitle("E2E Conflict")
        addTask(taskTitle)
        tapText("Sync")
        waitForSyncToFinish()
        waitForRowSyncState(taskTitle, "Synced")

        tapText("Server edit")
        waitForTextContains("Server updated")

        toggleCheckboxForTask(taskTitle)

        tapText("Sync")
        waitForTextContains("Conflict", timeoutMillis = 30_000)

        resolveConflictKeepLocal()

        waitForTextGone("Conflict — tap Resolve")
    }

    @Test
    fun deleteTask_removesFromList() {
        val taskTitle = uniqueTitle("E2E Delete")
        addTask(taskTitle)
        tapText("Delete")
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText(taskTitle).fetchSemanticsNodes().isEmpty()
        }
    }
}