package dev.syncforge.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Observes device network connectivity for reconnect-triggered sync.
 */
interface NetworkMonitor {
    val isOnline: Boolean
    fun observeOnline(): Flow<Boolean>
}

/** Default for tests and JVM — always online. */
object AlwaysOnlineNetworkMonitor : NetworkMonitor {
    override val isOnline: Boolean = true
    override fun observeOnline(): Flow<Boolean> = flowOf(true)
}