package dev.syncforge.sample.ui

import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Full sample-app scenario matrix: all tabs, sync, conflicts (edit + delete), clear-local pull restore,
 * and note→tag relationships. Requires [:mock-server] on host :8080 (./gradlew androidE2e).
 */
@RunWith(AndroidJUnit4::class)
class SampleScenariosE2ETest : SampleE2ETestBase() {

    @Before
    fun setUp() {
        prepareDevice()
    }

    @Test
    fun allTabs_areReachable() {
        navigateToTasks()
        waitForTextContains("New task")

        navigateToNotes()
        waitForTextContains("Add note")

        navigateToTags()
        waitForTextContains("Add")
    }

    @Test
    fun tasks_addSync_showsSynced() {
        val taskTitle = uniqueTitle("E2E Scenario Sync")
        addTask(taskTitle)
        syncAndWaitForIdle()
        waitForRowSyncState(taskTitle, "Synced", timeoutMillis = 45_000)
    }

    @Test
    fun tasks_editConflict_resolveKeepLocal() {
        val taskTitle = uniqueTitle("E2E Scenario EditConflict")
        addTask(taskTitle)
        syncAndWaitForIdle()
        waitForRowSyncState(taskTitle, "Synced")

        tapServerEdit()
        waitForTextContains("Server updated")
        toggleCheckboxForTask(taskTitle)

        syncAndWaitForIdle()
        waitForTextContains("Conflict", timeoutMillis = 30_000)
        waitForRowSyncState(taskTitle, "Conflict — tap Resolve")

        resolveConflictKeepLocal()
        waitForTextGone("Conflict — tap Resolve", timeoutMillis = 30_000)
    }

    @Test
    fun tasks_deleteConflict_resolveAcceptRemote_removesTask() {
        val taskTitle = uniqueTitle("E2E Scenario DeleteConflict")
        addTask(taskTitle)
        syncAndWaitForIdle()
        waitForRowSyncState(taskTitle, "Synced")

        tapServerDelete()
        waitForTextContains("Server deleted")
        toggleCheckboxForTask(taskTitle)

        syncAndWaitForIdle()
        waitForTextContains("Conflict", timeoutMillis = 30_000)
        waitForRowSyncState(taskTitle, "Conflict — tap Resolve")

        resolveConflictAcceptRemote()
        composeTestRule.waitUntil(timeoutMillis = 30_000) {
            composeTestRule.onAllNodesWithText(taskTitle).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun tasks_clearLocalDb_pullRestore_bringsBackSyncedTask() {
        val taskTitle = uniqueTitle("E2E Scenario PullRestore")
        addTask(taskTitle)
        syncAndWaitForIdle()
        waitForRowSyncState(taskTitle, "Synced")

        clearLocalDatabase()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithText(taskTitle).fetchSemanticsNodes().isEmpty()
        }

        syncAndWaitForIdle()
        waitForRowSyncState(taskTitle, "Synced", timeoutMillis = 45_000)
    }

    @Test
    fun tasks_localDelete_removesFromList() {
        val taskTitle = uniqueTitle("E2E Scenario LocalDelete")
        addTask(taskTitle)
        tapText("Delete")
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText(taskTitle).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun notes_addSync_showsSynced() {
        val noteTitle = uniqueTitle("E2E Scenario Note")
        addNote(noteTitle, body = "Scenario body")
        syncAndWaitForIdle()
        waitForRowSyncState(noteTitle, "Synced")
    }

    @Test
    fun tags_addSync_showsSynced() {
        val tagLabel = uniqueTitle("E2E Tag")
        addTag(tagLabel)
        syncAndWaitForIdle()
        waitForRowSyncState(tagLabel, "Synced")
    }

    @Test
    fun notes_withTag_showsTagLabelAfterSync() {
        val tagLabel = uniqueTitle("E2E Rel Tag")
        val noteTitle = uniqueTitle("E2E Rel Note")

        addTag(tagLabel)
        syncAndWaitForIdle()
        waitForRowSyncState(tagLabel, "Synced")

        addNote(noteTitle, body = "Tagged note", tagLabel = tagLabel)
        syncAndWaitForIdle()
        waitForRowSyncState(noteTitle, "Synced")
        waitForTextContains("Tag: $tagLabel")
    }

    @Test
    fun multiEntity_taskConflict_noteStillSyncs() {
        val taskTitle = uniqueTitle("E2E Iso Task")
        val noteTitle = uniqueTitle("E2E Iso Note")

        addTask(taskTitle)
        syncAndWaitForIdle()
        waitForRowSyncState(taskTitle, "Synced")

        tapServerEdit()
        waitForTextContains("Server updated")
        toggleCheckboxForTask(taskTitle)
        syncAndWaitForIdle()
        waitForRowSyncState(taskTitle, "Conflict — tap Resolve")

        addNote(noteTitle)
        syncAndWaitForIdle()
        waitForRowSyncState(noteTitle, "Synced")

        navigateToTasks()
        waitForTextContains("Conflict")
    }
}