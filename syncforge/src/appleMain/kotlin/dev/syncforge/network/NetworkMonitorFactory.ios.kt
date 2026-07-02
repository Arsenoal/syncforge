package dev.syncforge.network

/**
 * iOS factory for [NetworkMonitor] — no [android.content.Context] required.
 */
object NetworkMonitorFactory {

    fun create(): NetworkMonitor = IosNetworkMonitor()
}