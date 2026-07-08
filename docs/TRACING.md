# Structured tracing

Opt-in OpenTelemetry-compatible spans for push, pull, conflict, and retry operations.
Default is [SyncTracer.None](../syncforge/src/commonMain/kotlin/dev/syncforge/trace/SyncTracer.kt) —
**zero allocation** when disabled (1.5-01 acceptance).

---

## Quick start (Android)

```kotlin
implementation("studio.syncforge:syncforge-integration-opentelemetry:1.2.0")

@OptIn(ExperimentalSyncForgeApi::class)
SyncForge.android(this) {
    tracing(OpenTelemetrySyncTracer(GlobalOpenTelemetry.getTracer("syncforge")))
    baseUrl(BuildConfig.SYNC_BASE_URL)
    registry(SyncForgeHandlers.registry(taskDao))
}
```

Without `tracing(...)`, SyncForge uses [SyncTracer.None](../syncforge/src/commonMain/kotlin/dev/syncforge/trace/SyncTracer.kt) — no span objects are created.

---

## Span catalog

| Span | When | Key attributes |
|------|------|----------------|
| `syncforge.sync` | [SyncManager.sync] full cycle | `syncforge.operation=full_sync` |
| `syncforge.push` | Push batch to transport | `syncforge.batch.size`, `syncforge.acknowledged.count` |
| `syncforge.pull` | Pull + apply deltas | `syncforge.since.millis`, `syncforge.pulled.count` |
| `syncforge.conflict` | Deferred/auto/user conflict | `syncforge.entity.type`, `syncforge.conflict.outcome` |
| `syncforge.retry` | Retry scheduled or executed | `syncforge.retry.delay_ms`, `syncforge.operation` |

Attribute keys: [SyncTraceAttributes](../syncforge/src/commonMain/kotlin/dev/syncforge/trace/SyncTraceAttributes.kt).

---

## Custom tracer

Implement [SyncTracer](../syncforge/src/commonMain/kotlin/dev/syncforge/trace/SyncTracer.kt) and
[SyncSpan](../syncforge/src/commonMain/kotlin/dev/syncforge/trace/SyncSpan.kt) for your APM
(logging, Datadog, Sentry performance, etc.):

```kotlin
class LoggingSyncTracer : SyncTracer {
    override val isEnabled: Boolean = true

    override fun startSpan(name: SyncSpanName, attributes: Map<String, String>): SyncSpan {
        logger.info("span.start ${name.otelName} $attributes")
        return object : SyncSpan {
            override fun setAttribute(key: String, value: String) { /* … */ }
            override fun setAttribute(key: String, value: Long) = setAttribute(key, value.toString())
            override fun recordException(error: Throwable) { logger.warn("span.error", error) }
            override fun end(status: SyncSpanStatus) {
                logger.info("span.end ${name.otelName} status=$status")
            }
        }
    }
}
```

Wire via `SyncForge.builder { syncTracer = LoggingSyncTracer() }` or `tracing(...)` on platform DSLs.

---

## OTLP exporter sample (JVM / Android)

OpenTelemetry SDK setup is app-owned — SyncForge only emits spans via the API bridge.

```kotlin
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.semconv.ResourceAttributes

fun installSyncForgeTracing(serviceName: String): OpenTelemetrySyncTracer {
    val exporter = OtlpGrpcSpanExporter.builder()
        .setEndpoint("https://otel-collector.example.com:4317")
        .build()
    val provider = SdkTracerProvider.builder()
        .setResource(Resource.getDefault().toBuilder()
            .put(ResourceAttributes.SERVICE_NAME, serviceName)
            .build())
        .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
        .build()
    val openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(provider)
        .build()
    GlobalOpenTelemetry.set(openTelemetry)
    return OpenTelemetrySyncTracer(openTelemetry.getTracer("syncforge"))
}
```

Add SDK dependencies in your app (not in `:syncforge` core):

```kotlin
implementation("io.opentelemetry:opentelemetry-sdk:1.49.0")
implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.49.0")
implementation("io.opentelemetry.semconv:opentelemetry-semconv:1.30.0")
```

---

## Testing

Use [RecordingSyncTracer](../syncforge/src/commonTest/kotlin/dev/syncforge/trace/RecordingSyncTracer.kt)
in unit tests (see [SyncTraceTest](../syncforge/src/commonTest/kotlin/dev/syncforge/trace/SyncTraceTest.kt)).

---

## Related

- [CUSTOM_TRANSPORT.md](CUSTOM_TRANSPORT.md) — transport layer (separate from tracing)
- [SyncDebug / SyncHealth](RECIPES.md#use-the-in-app-debug-console) — in-app debug console (experimental)