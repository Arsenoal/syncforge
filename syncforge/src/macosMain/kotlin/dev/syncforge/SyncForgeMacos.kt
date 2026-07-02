package dev.syncforge

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.sync.SyncManager

/**
 * macOS setup — delegates to [SyncForge.ios] defaults (SQLDelight, UserDefaults, NWPathMonitor).
 */
@ExperimentalSyncForgeApi
fun SyncForge.macos(block: IosSyncForgeDsl.() -> Unit): SyncManager =
    ios(block)