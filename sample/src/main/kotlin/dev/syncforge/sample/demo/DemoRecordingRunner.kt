package dev.syncforge.sample.demo

import dev.syncforge.sample.BuildConfig
import dev.syncforge.sample.SampleApplication
import dev.syncforge.sample.tasks.DevSyncClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

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
        delay(3_000)

        app.taskRepository.addTask("Buy milk")
        DemoActivityLog.log("Step 1/5 — task added (Room + outbox)", highlight = true)
        delay(2_500)

        DemoActivityLog.log("Step 2/5 — push to mock-server", highlight = true)
        app.syncManager.sync()
        delay(4_000)

        val task = app.taskRepository.observeTasks().first().firstOrNull()
        if (task != null) {
            DemoActivityLog.log(
                "Step 3/5 — server title edit + local checkbox → gitLike auto-merge",
                highlight = true,
            )
            DevSyncClient.simulateServerEdit(task, "${task.title} (server)")
            delay(1_500)
            app.taskRepository.toggleCompleted(task)
            delay(1_000)
            app.syncManager.sync()
            delay(4_000)
        }

        DemoActivityLog.log("Step 4/5 — clear local Room DB + cursor", highlight = true)
        app.resetForDemoPresentation()
        delay(3_500)

        DemoActivityLog.log("Step 5/5 — pull remote tasks into empty Room", highlight = true)
        app.syncManager.sync()
        delay(4_500)

        DemoActivityLog.log("Auto-demo complete", highlight = true)
        delay(2_000)
    }
}