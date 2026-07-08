package dev.syncforge

import dev.syncforge.api.ExperimentalSyncForgeApi

/**
 * Compile-time guard for [SyncForge.web] DSL surface — does not invoke [WebSyncForgeDsl.build].
 */
@OptIn(ExperimentalSyncForgeApi::class)
@Suppress("unused")
internal val webDslCompileCheck: suspend WebSyncForgeDsl.() -> Unit = {
    baseUrl("http://127.0.0.1:8080")
    databaseName("compile-check.db")
    networkMonitorAlwaysOnline()
    syncOnTabVisible(enabled = false)
}