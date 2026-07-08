package dev.syncforge.sample.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.syncforge.sample.tasks.taskLocalEditTitle
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two-emulator E2E (1.4-06) — Gradle [androidMultiDeviceE2e] runs each `#phase_*` method on
 * device A or B in sequence. Shared state lives on mock-server `/dev/e2e/session`.
 */
@RunWith(AndroidJUnit4::class)
class MultiDeviceE2ETest : SampleE2ETestBase() {

    @Test
    fun phase_deviceA_createTaskAndSync() {
        assumeDeviceRole("A")
        assumeMockServerHealthy()
        resetMockServerAndApp()
        waitForAppReady()

        val sessionId = sessionId()
        MultiDeviceE2EClient.createSession(sessionId)

        val title = "E2E Multi ${System.currentTimeMillis()}"
        addTask(title)
        syncAndWaitForIdle()
        waitForRowSyncState(title, "Synced", timeoutMillis = 45_000)

        MultiDeviceE2EClient.put(sessionId, KEY_TASK_TITLE, title)
        MultiDeviceE2EClient.put(sessionId, KEY_DEVICE_A_READY, "true")
    }

    @Test
    fun phase_deviceB_pullTask() {
        assumeDeviceRole("B")
        assumeMockServerHealthy()
        resetAppOnly()
        waitForAppReady()

        val sessionId = sessionId()
        MultiDeviceE2EClient.waitForKey(sessionId, KEY_DEVICE_A_READY)
        val title = MultiDeviceE2EClient.waitForKey(sessionId, KEY_TASK_TITLE)

        syncAndWaitForIdle()
        waitForRowSyncState(title, "Synced", timeoutMillis = 45_000)
        MultiDeviceE2EClient.put(sessionId, KEY_DEVICE_B_READY, "true")
    }

    @Test
    fun phase_deviceA_localEdit() {
        assumeDeviceRole("A")
        assumeMockServerHealthy()
        waitForAppReady()

        val sessionId = sessionId()
        MultiDeviceE2EClient.waitForKey(sessionId, KEY_DEVICE_B_READY)
        val title = MultiDeviceE2EClient.waitForKey(sessionId, KEY_TASK_TITLE)

        tapTaskLocalEdit(title)
        val localTitle = taskLocalEditTitle(title)
        waitForRowSyncState(localTitle, "Pending", timeoutMillis = 30_000)
        MultiDeviceE2EClient.put(sessionId, KEY_DEVICE_A_EDITED, "true")
    }

    @Test
    fun phase_deviceB_localEditAndSync() {
        assumeDeviceRole("B")
        assumeMockServerHealthy()
        waitForAppReady()

        val sessionId = sessionId()
        MultiDeviceE2EClient.waitForKey(sessionId, KEY_DEVICE_A_EDITED)
        val title = MultiDeviceE2EClient.waitForKey(sessionId, KEY_TASK_TITLE)

        tapTaskLocalEdit(title)
        val localTitle = taskLocalEditTitle(title)
        waitForRowSyncState(localTitle, "Pending", timeoutMillis = 30_000)

        syncAndWaitForIdle()
        waitForRowSyncState(localTitle, "Synced", timeoutMillis = 45_000)
        MultiDeviceE2EClient.put(sessionId, KEY_DEVICE_B_SYNCED, "true")
    }

    @Test
    fun phase_deviceA_syncExpectConflict() {
        assumeDeviceRole("A")
        assumeMockServerHealthy()
        waitForAppReady()

        val sessionId = sessionId()
        MultiDeviceE2EClient.waitForKey(sessionId, KEY_DEVICE_B_SYNCED)
        val title = MultiDeviceE2EClient.waitForKey(sessionId, KEY_TASK_TITLE)
        val localTitle = taskLocalEditTitle(title)

        syncAndWaitForIdle()
        waitForRowSyncState(localTitle, "Conflict — tap Resolve", timeoutMillis = 45_000)
    }

    @Test
    fun phase_deviceA_createTagAndSync() {
        assumeDeviceRole("A")
        assumeMockServerHealthy()
        resetMockServerAndApp()
        waitForAppReady()

        val sessionId = sessionId()
        MultiDeviceE2EClient.createSession(sessionId)

        val label = "E2E Tag ${System.currentTimeMillis()}"
        addTag(label)
        syncAndWaitForIdle()
        waitForRowSyncState(label, "Synced", timeoutMillis = 45_000)

        MultiDeviceE2EClient.put(sessionId, KEY_TAG_LABEL, label)
        MultiDeviceE2EClient.put(sessionId, KEY_DEVICE_A_READY, "true")
    }

    @Test
    fun phase_deviceB_pullTag() {
        assumeDeviceRole("B")
        assumeMockServerHealthy()
        resetAppOnly()
        waitForAppReady()

        val sessionId = sessionId()
        MultiDeviceE2EClient.waitForKey(sessionId, KEY_DEVICE_A_READY)
        val label = MultiDeviceE2EClient.waitForKey(sessionId, KEY_TAG_LABEL)

        syncAndWaitForIdle()
        waitForRowSyncState(label, "Synced", timeoutMillis = 45_000)
        MultiDeviceE2EClient.put(sessionId, KEY_DEVICE_B_READY, "true")
    }

    @Test
    fun phase_deviceA_tagLocalEditPending() {
        assumeDeviceRole("A")
        assumeMockServerHealthy()
        waitForAppReady()

        val sessionId = sessionId()
        MultiDeviceE2EClient.waitForKey(sessionId, KEY_DEVICE_B_READY)
        val label = MultiDeviceE2EClient.waitForKey(sessionId, KEY_TAG_LABEL)

        tapTagLocalEdit(label)
        val localLabel = tagLocalLabel(label)
        waitForRowSyncState(localLabel, "Pending", timeoutMillis = 30_000)
        MultiDeviceE2EClient.put(sessionId, KEY_DEVICE_A_EDITED, "true")
    }

    @Test
    fun phase_deviceB_tagLocalEditNewerAndSync() {
        assumeDeviceRole("B")
        assumeMockServerHealthy()
        waitForAppReady()

        val sessionId = sessionId()
        MultiDeviceE2EClient.waitForKey(sessionId, KEY_DEVICE_A_EDITED)
        val label = MultiDeviceE2EClient.waitForKey(sessionId, KEY_TAG_LABEL)
        val serverLabel = tagServerLabel(label)

        val serverUpdatedAtMillis = simulateServerTagEdit(label, serverLabel)
        applyTagLocalEditNewerThan(label, serverUpdatedAtMillis)
        val localLabel = tagLocalLabel(label)
        waitForRowSyncState(localLabel, "Pending", timeoutMillis = 30_000)

        syncAndWaitForIdle()
        waitForRowSyncState(localLabel, "Pending", timeoutMillis = 45_000)
        MultiDeviceE2EClient.put(sessionId, KEY_DEVICE_B_SYNCED, "true")
    }

    @Test
    fun phase_deviceA_tagSyncExpectRemoteWins() {
        assumeDeviceRole("A")
        assumeMockServerHealthy()
        waitForAppReady()

        val sessionId = sessionId()
        MultiDeviceE2EClient.waitForKey(sessionId, KEY_DEVICE_B_SYNCED)
        val label = MultiDeviceE2EClient.waitForKey(sessionId, KEY_TAG_LABEL)
        val localLabel = tagLocalLabel(label)
        val serverLabel = tagServerLabel(label)

        syncAndWaitForIdle()
        waitForRowSyncState(serverLabel, "Synced", timeoutMillis = 45_000)
        waitForTextGone(localLabel, timeoutMillis = 15_000)
    }

    private fun assumeDeviceRole(expected: String) {
        val role = InstrumentationRegistry.getArguments().getString("deviceRole")
        Assume.assumeTrue("Expected deviceRole=$expected but was $role", role == expected)
    }

    private fun sessionId(): String =
        InstrumentationRegistry.getArguments().getString("sessionId")
            ?: error("sessionId instrumentation argument required")

    private companion object {
        const val KEY_TASK_TITLE = "taskTitle"
        const val KEY_TAG_LABEL = "tagLabel"
        const val KEY_DEVICE_A_READY = "deviceA_ready"
        const val KEY_DEVICE_B_READY = "deviceB_ready"
        const val KEY_DEVICE_A_EDITED = "deviceA_edited"
        const val KEY_DEVICE_B_SYNCED = "deviceB_synced"
    }
}