package dev.syncforge.work

import dev.syncforge.sync.SyncManager
import dev.syncforge.sync.SyncWorkScheduler

internal expect object AppleBackgroundSyncPlatform {
    fun createWorkScheduler(taskIdentifier: String): SyncWorkScheduler

    fun onManagerBuilt(
        manager: SyncManager,
        taskIdentifier: String,
        schedulePeriodicSyncOnStart: Boolean,
    )
}