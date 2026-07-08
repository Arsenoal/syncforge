package dev.syncforge

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.network.createWebKtorSyncTransport

/**
 * Compile-time guard for [SyncForge.web] and [createWebKtorSyncTransport] — does not invoke build.
 */
@OptIn(ExperimentalSyncForgeApi::class)
@Suppress("unused")
internal val webDslCompileCheck: suspend WebSyncForgeDsl.() -> Unit = {
    baseUrl("http://127.0.0.1:8080")
    databaseName("compile-check.db")
    networkMonitorAlwaysOnline()
    syncOnTabVisible(enabled = false)
}

@OptIn(ExperimentalSyncForgeApi::class)
@Suppress("unused")
private val webTransportCompileCheck = createWebKtorSyncTransport("http://127.0.0.1:8080")