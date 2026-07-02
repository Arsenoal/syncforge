package dev.syncforge.network

import kotlin.concurrent.Volatile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

/**
 * Observes connectivity via Apple's Network framework ([nw_path_monitor_create]).
 *
 * Mirrors [AndroidNetworkMonitor] — drives reconnect auto-push in [dev.syncforge.sync.SyncManagerImpl].
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosNetworkMonitor : NetworkMonitor {

    @Volatile
    private var currentOnline: Boolean = true

    override val isOnline: Boolean
        get() = currentOnline

    override fun observeOnline(): Flow<Boolean> = callbackFlow {
        val monitor = nw_path_monitor_create()
        val queue = dispatch_queue_create("dev.syncforge.network", null)

        nw_path_monitor_set_update_handler(monitor) { path ->
            val satisfied = nw_path_get_status(path) == nw_path_status_satisfied
            currentOnline = satisfied
            trySend(satisfied)
        }

        nw_path_monitor_set_queue(monitor, queue)
        nw_path_monitor_start(monitor)

        awaitClose {
            nw_path_monitor_cancel(monitor)
        }
    }.distinctUntilChanged()
}