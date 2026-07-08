package dev.syncforge.network

object NetworkMonitorFactory {

    fun create(): NetworkMonitor = BrowserNetworkMonitor()
}