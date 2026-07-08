package dev.syncforge.network

import dev.syncforge.sync.SyncManager
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.w3c.dom.events.Event

/**
 * Triggers [SyncManager.sync] when the tab becomes visible and the browser reports online.
 *
 * Complements [BrowserNetworkMonitor] reconnect push — useful when users return to a backgrounded tab.
 * There is no OS-level background sync on web (no WorkManager).
 */
class WebVisibilitySyncTrigger(
    private val syncManager: SyncManager,
    private val scope: CoroutineScope,
    private val networkMonitor: NetworkMonitor = BrowserNetworkMonitor(),
) {
    private var listener: ((Event) -> Unit)? = null

    fun start() {
        if (listener != null) return
        val handler: (Event) -> Unit = {
            if (isDocumentVisible() && networkMonitor.isOnline) {
                scope.launch { syncManager.sync() }
            }
        }
        listener = handler
        document.addEventListener("visibilitychange", handler)
    }

    fun stop() {
        listener?.let { document.removeEventListener("visibilitychange", it) }
        listener = null
    }

    private fun isDocumentVisible(): Boolean =
        js("document.visibilityState === \"visible\"") as Boolean
}