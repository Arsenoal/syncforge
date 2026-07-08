package dev.syncforge.integration.opentelemetry

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.trace.SyncSpan
import dev.syncforge.trace.SyncSpanName
import dev.syncforge.trace.SyncSpanStatus
import dev.syncforge.trace.SyncTracer
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer

/**
 * Maps [SyncTracer] to the OpenTelemetry API — export via your SDK
 * (`SdkTracerProvider`, OTLP exporter, etc.).
 */
@ExperimentalSyncForgeApi
class OpenTelemetrySyncTracer(
    private val tracer: Tracer,
) : SyncTracer {
    override val isEnabled: Boolean = true

    override fun startSpan(name: SyncSpanName, attributes: Map<String, String>): SyncSpan {
        var builder = tracer.spanBuilder(name.otelName).setSpanKind(SpanKind.CLIENT)
        attributes.forEach { (key, value) -> builder = builder.setAttribute(key, value) }
        return OtelSyncSpan(builder.startSpan())
    }
}

private class OtelSyncSpan(
    private val span: Span,
) : SyncSpan {
    override fun setAttribute(key: String, value: String) {
        span.setAttribute(key, value)
    }

    override fun setAttribute(key: String, value: Long) {
        span.setAttribute(key, value)
    }

    override fun recordException(error: Throwable) {
        span.recordException(error)
    }

    override fun end(status: SyncSpanStatus) {
        when (status) {
            SyncSpanStatus.OK -> span.setStatus(StatusCode.OK)
            SyncSpanStatus.ERROR -> span.setStatus(StatusCode.ERROR)
        }
        span.end()
    }
}