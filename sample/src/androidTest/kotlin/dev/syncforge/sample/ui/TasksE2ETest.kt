package dev.syncforge.sample.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
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
        waitForAnyText("Up to date", "Synced", "Last synced")
    }

    @Test
    fun conflictFlow_serverEditThenLocalEdit_allowsResolution() {
        val taskTitle = uniqueTitle("E2E Conflict")
        addTask(taskTitle)
        tapText("Sync")
        waitForSyncToFinish()

        tapText("Server edit")
        device.wait(Until.hasObject(By.textContains("Server updated")), 15_000)

        device.findObject(By.checkable(true)).click()

        tapText("Sync")
        device.wait(Until.hasObject(By.textContains("Conflict")), 30_000)

        tapText("Resolve")
        tapText("Keep mine")

        device.wait(Until.gone(By.textContains("Conflict — tap Resolve")), 15_000)
    }

    @Test
    fun deleteTask_removesFromList() {
        val taskTitle = uniqueTitle("E2E Delete")
        addTask(taskTitle)
        tapText("Delete")
        device.wait(Until.gone(By.text(taskTitle)), 10_000)
    }
}