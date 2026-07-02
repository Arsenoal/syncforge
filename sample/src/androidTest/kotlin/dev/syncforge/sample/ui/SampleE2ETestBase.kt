package dev.syncforge.sample.ui

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assume
import java.net.HttpURLConnection
import java.net.URL

/**
 * Shared UiAutomator helpers for [:sample] E2E tests against [:mock-server] on the host
 * (10.0.2.2:8080 from the emulator).
 */
abstract class SampleE2ETestBase {

    protected lateinit var device: UiDevice

    protected fun prepareDevice() {
        Assume.assumeTrue(
            "Mock server must be running on host port 8080 (./gradlew androidE2e)",
            isMockServerHealthy(),
        )
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        launchApp()
    }

    protected fun launchApp() {
        device.pressHome()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: error("Launch intent missing for ${context.packageName}")
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        device.wait(Until.hasObject(By.text("Sync")), 15_000)
        device.wait(Until.hasObject(By.text("New task")), 15_000)
    }

    protected fun navigateToTasks() {
        tapText("Tasks")
        device.wait(Until.hasObject(By.text("New task")), 10_000)
    }

    protected fun navigateToNotes() {
        tapText("Notes")
        device.wait(Until.hasObject(By.text("Add note")), 10_000)
    }

    protected fun addTask(title: String) {
        navigateToTasks()
        val field = device.wait(Until.findObject(By.text("New task")), 5_000)
        field.click()
        field.text = title
        tapText("Add")
        device.wait(Until.hasObject(By.text(title)), 10_000)
    }

    protected fun addNote(title: String, body: String = "") {
        navigateToNotes()
        val fields = device.wait(
            Until.findObjects(By.clazz("android.widget.EditText")),
            5_000,
        )
        check(fields.isNotEmpty()) { "Note title field not found" }
        fields[0].click()
        fields[0].text = title
        if (body.isNotBlank()) {
            check(fields.size >= 2) { "Note body field not found" }
            fields[1].click()
            fields[1].text = body
        }
        tapText("Add note")
        device.wait(Until.hasObject(By.text(title)), 10_000)
    }

    protected fun tapText(text: String) {
        val node = device.wait(Until.findObject(By.text(text)), 10_000)
        node.click()
    }

    protected fun waitForSyncToFinish(timeoutMillis: Long = 30_000) {
        device.wait(Until.gone(By.textContains("Syncing")), timeoutMillis)
    }

    protected fun waitForAnyText(vararg options: String) {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (options.any { device.hasObject(By.textContains(it)) }) return
            Thread.sleep(250)
        }
        error("Expected one of ${options.toList()} on screen")
    }

    protected fun waitForRowSyncState(itemTitle: String, stateLabel: String) {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (device.hasObject(By.text(itemTitle)) && device.hasObject(By.text(stateLabel))) {
                return
            }
            Thread.sleep(250)
        }
        error("Expected '$itemTitle' with sync state '$stateLabel'")
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