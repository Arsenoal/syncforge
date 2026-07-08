package dev.syncforge.network

import kotlin.concurrent.Volatile
import kotlinx.browser.window
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Observes browser connectivity via [window.navigator.onLine] and `online` / `offline` events.
 */
internal class BrowserNetworkMonitor : NetworkMonitor {

    @Volatile
    private var currentOnline: Boolean = window.navigator.onLine

    override val isOnline: Boolean
        get() = currentOnline

    override fun observeOnline(): Flow<Boolean> = callbackFlow {
        val listener: (dynamic) -> Unit = {
            currentOnline = window.navigator.onLine
            trySend(currentOnline)
        }
        window.addEventListener("online", listener)
        window.addEventListener("offline", listener)
        trySend(window.navigator.onLine)
        awaitClose {
            window.removeEventListener("online", listener)
            window.removeEventListener("offline", listener)
        }
    }.distinctUntilChanged()
}