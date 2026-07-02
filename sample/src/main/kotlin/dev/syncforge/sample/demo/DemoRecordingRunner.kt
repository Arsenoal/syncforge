package dev.syncforge.sample.demo

import dev.syncforge.sample.BuildConfig
import dev.syncforge.sample.SampleApplication
import kotlinx.coroutines.delay

/**
 * Runs the README demo sequence in-process — no [adb shell input tap] (avoids opening
 * launcher / calendar / keyboard chrome during screen recordings).
 */
object DemoRecordingRunner {

    const val EXTRA_AUTO_DEMO = "auto_demo"

    @Volatile
    var inProgress: Boolean = false
        private set

    suspend fun run(app: SampleApplication) {
        if (!BuildConfig.DEBUG) return

        inProgress = true
        try {
            runSequence(app)
        } finally {
            inProgress = false
        }
    }

    private suspend fun runSequence(app: SampleApplication) {
        DemoActivityLog.log("Auto-demo starting…", highlight = true)
        delay(4_000)

        app.taskRepository.addTask("Buy milk")
        DemoActivityLog.log("Step 1/4 — task added (optimistic Room write + outbox)", highlight = true)
        delay(3_000)

        DemoActivityLog.log("Step 2/4 — syncing to mock-server (push)", highlight = true)
        app.syncManager.sync()
        delay(5_000)

        DemoActivityLog.log("Step 3/4 — clearing local Room DB + cursor", highlight = true)
        app.resetForDemoPresentation()
        delay(4_000)

        DemoActivityLog.log("Step 4/4 — pulling remote tasks into empty Room", highlight = true)
        app.syncManager.sync()
        delay(5_000)

        DemoActivityLog.log("Auto-demo complete", highlight = true)
    }
}