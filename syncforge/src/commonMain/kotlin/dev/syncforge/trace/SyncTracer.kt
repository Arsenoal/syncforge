package dev.syncforge.trace

import dev.syncforge.api.ExperimentalSyncForgeApi

/**
 * Opt-in tracing port for sync operations. Default [NoOpSyncTracer] performs no work.
 *
 * Map to OpenTelemetry via `:syncforge-integration-opentelemetry` or a custom implementation.
 */
@ExperimentalSyncForgeApi
interface SyncTracer {
    /** When `false`, [SyncTrace.runSpan] skips span allocation entirely. */
    val isEnabled: Boolean

    fun startSpan(name: SyncSpanName, attributes: Map<String, String> = emptyMap()): SyncSpan

    companion object {
        /** Disabled tracer — zero overhead when passed to [SyncManager]. */
        val None: SyncTracer = NoOpSyncTracer
    }
}