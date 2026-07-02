package dev.syncforge.work

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.sync.SyncWorkScheduler
import kotlin.time.Duration

@ExperimentalSyncForgeApi
internal class IosBackgroundSyncWorkScheduler(
    private val taskIdentifier: String,
) : SyncWorkScheduler {

    override fun schedulePeriodic(interval: Duration) {
        IosBackgroundSync.scheduleAppRefresh(interval, taskIdentifier)
    }

    override fun scheduleRetry(delay: Duration) {
        IosBackgroundSync.scheduleRetry(delay, taskIdentifier)
    }

    override fun cancel() {
        IosBackgroundSync.cancel(taskIdentifier)
    }
}