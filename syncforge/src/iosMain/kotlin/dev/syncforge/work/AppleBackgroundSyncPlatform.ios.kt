package dev.syncforge.work

import dev.syncforge.sync.SyncManager
import dev.syncforge.sync.SyncWorkScheduler

internal actual object AppleBackgroundSyncPlatform {
    actual fun createWorkScheduler(taskIdentifier: String): SyncWorkScheduler =
        IosBackgroundSyncWorkScheduler(taskIdentifier)

    actual fun onManagerBuilt(
        manager: SyncManager,
        taskIdentifier: String,
        schedulePeriodicSyncOnStart: Boolean,
    ) {
        IosBackgroundSync.bind { manager.sync() }
        if (schedulePeriodicSyncOnStart) {
            manager.schedulePeriodicSync()
        }
    }
}