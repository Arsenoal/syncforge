# OpenTelemetry tracing bridge

Maps [SyncTracer](../syncforge/src/commonMain/kotlin/dev/syncforge/trace/SyncTracer.kt) to the
OpenTelemetry API. Pair with your SDK (`SdkTracerProvider`, OTLP exporter, etc.).

```kotlin
import dev.syncforge.integration.opentelemetry.OpenTelemetrySyncTracer
import io.opentelemetry.api.GlobalOpenTelemetry

@OptIn(ExperimentalSyncForgeApi::class)
SyncForge.android(this) {
    tracing(OpenTelemetrySyncTracer(GlobalOpenTelemetry.getTracer("syncforge")))
    baseUrl(BuildConfig.SYNC_BASE_URL)
    registry(handlers)
}
```

Span names: `syncforge.sync`, `syncforge.push`, `syncforge.pull`, `syncforge.conflict`, `syncforge.retry`.

See [docs/TRACING.md](../docs/TRACING.md) for a full OTLP exporter sample.