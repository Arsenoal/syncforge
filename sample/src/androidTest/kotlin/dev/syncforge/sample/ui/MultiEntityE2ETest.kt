package dev.syncforge.sample.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Multi-entity E2E coverage for the 1.0 P0 Sample App Proof:
 * tasks + notes on one [dev.syncforge.sync.SyncManager], shared outbox, independent sync.
 */
@RunWith(AndroidJUnit4::class)
class MultiEntityE2ETest : SampleE2ETestBase() {

    @Before
    fun setUp() {
        prepareDevice()
    }

    @Test
    fun addTaskAndNote_singleSync_bothEntitiesReachSyncedState() {
        val taskTitle = uniqueTitle("E2E Multi Task")
        val noteTitle = uniqueTitle("E2E Multi Note")

        addTask(taskTitle)
        addNote(noteTitle, body = "Created during multi-entity E2E")

        tapText("Sync")
        waitForSyncToFinish()
        waitForAnyText("Up to date", "Synced", "Last synced")

        waitForRowSyncState(noteTitle, "Synced", timeoutMillis = 60_000)

        navigateToTasks()
        waitForRowSyncState(taskTitle, "Synced", timeoutMillis = 60_000)
    }

    @Test
    fun taskConflict_doesNotBlockNoteSync() {
        val taskTitle = uniqueTitle("E2E Conflict Task")
        val noteTitle = uniqueTitle("E2E Conflict Note")

        addTask(taskTitle)
        tapText("Sync")
        waitForSyncToFinish()
        waitForRowSyncState(taskTitle, "Synced")

        tapText("Server edit")
        waitForTextContains("Server updated")
        toggleCheckboxForTask(taskTitle)

        tapText("Sync")
        waitForTextContains("Conflict", timeoutMillis = 30_000)
        waitForRowSyncState(taskTitle, "Conflict — tap Resolve")

        addNote(noteTitle)
        tapText("Sync")
        waitForSyncToFinish()

        waitForRowSyncState(noteTitle, "Synced", timeoutMillis = 60_000)

        navigateToTasks()
        waitForTextContains("Conflict")
    }
}