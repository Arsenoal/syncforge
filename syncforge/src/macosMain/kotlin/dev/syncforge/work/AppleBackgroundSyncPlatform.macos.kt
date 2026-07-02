package dev.syncforge.work

import dev.syncforge.sync.NoOpSyncWorkScheduler
import dev.syncforge.sync.SyncManager
import dev.syncforge.sync.SyncWorkScheduler

internal actual object AppleBackgroundSyncPlatform {
    actual fun createWorkScheduler(@Suppress("UNUSED_PARAMETER") taskIdentifier: String): SyncWorkScheduler =
        NoOpSyncWorkScheduler

    actual fun onManagerBuilt(
        manager: SyncManager,
        @Suppress("UNUSED_PARAMETER") taskIdentifier: String,
        schedulePeriodicSyncOnStart: Boolean,
    ) {
        if (schedulePeriodicSyncOnStart) {
            manager.schedulePeriodicSync()
        }
    }
}