package dev.syncforge.trace

import dev.syncforge.api.ExperimentalSyncForgeApi

/**
 * Active span handle — set attributes, record errors, then [end].
 *
 * Implementations may map to OpenTelemetry `Span`, logging MDC, or custom APM SDKs.
 */
@ExperimentalSyncForgeApi
interface SyncSpan {
    fun setAttribute(key: String, value: String)
    fun setAttribute(key: String, value: Long)
    fun recordException(error: Throwable)
    fun end(status: SyncSpanStatus = SyncSpanStatus.OK)
}