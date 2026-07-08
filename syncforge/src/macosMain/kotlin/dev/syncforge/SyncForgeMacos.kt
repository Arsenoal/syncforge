package dev.syncforge

import dev.syncforge.sync.SyncManager

/**
 * macOS setup — delegates to [SyncForge.ios] defaults (SQLDelight, UserDefaults, NWPathMonitor).
 */
fun SyncForge.macos(block: IosSyncForgeDsl.() -> Unit): SyncManager =
    ios(block)