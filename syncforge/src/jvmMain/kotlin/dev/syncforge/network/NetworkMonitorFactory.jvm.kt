package dev.syncforge.network

/**
 * JVM desktop defaults to [AlwaysOnlineNetworkMonitor].
 * Override via [SyncForge.desktop] { networkMonitor(...) } when needed.
 */
object NetworkMonitorFactory {
    fun create(): NetworkMonitor = AlwaysOnlineNetworkMonitor
}