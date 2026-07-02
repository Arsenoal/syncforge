package dev.syncforge.sample.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/**
 * End-to-end tests against [:mock-server] on the host (10.0.2.2:8080 from the emulator).
 *
 * Uses UiAutomator instead of Compose Test to avoid Espresso [InputManager] issues on some API 35 AVDs.
 */
@RunWith(AndroidJUnit4::class)
class TasksE2ETest {

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        assumeTrue(
            "Mock server must be running on host port 8080 (./gradlew androidE2e)",
            isMockServerHealthy(),
        )
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        launchApp()
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

    private fun launchApp() {
        device.pressHome()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: error("Launch intent missing for ${context.packageName}")
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        device.wait(Until.hasObject(By.text("SyncForge Tasks")), 10_000)
    }

    private fun addTask(title: String) {
        val field = device.wait(Until.findObject(By.text("New task")), 5_000)
        field.click()
        field.text = title
        tapText("Add")
        device.wait(Until.hasObject(By.text(title)), 10_000)
    }

    private fun tapText(text: String) {
        val node = device.wait(Until.findObject(By.text(text)), 10_000)
        node.click()
    }

    private fun waitForSyncToFinish(timeoutMillis: Long = 30_000) {
        device.wait(Until.gone(By.textContains("Syncing")), timeoutMillis)
    }

    private fun waitForAnyText(vararg options: String) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (options.any { device.hasObject(By.textContains(it)) }) return
            Thread.sleep(250)
        }
        error("Expected one of ${options.toList()} on screen")
    }

    private fun uniqueTitle(prefix: String): String =
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