package dev.syncforge.trace

import dev.syncforge.api.ExperimentalSyncForgeApi

/** Terminal span status — maps to OpenTelemetry [io.opentelemetry.api.trace.StatusCode]. */
@ExperimentalSyncForgeApi
enum class SyncSpanStatus {
    OK,
    ERROR,
}