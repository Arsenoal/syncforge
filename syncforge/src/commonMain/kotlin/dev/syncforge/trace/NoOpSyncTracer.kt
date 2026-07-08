package dev.syncforge.trace

internal object NoOpSyncTracer : SyncTracer {
    override val isEnabled: Boolean get() = false

    override fun startSpan(name: SyncSpanName, attributes: Map<String, String>): SyncSpan =
        NoOpSyncSpan
}

internal object NoOpSyncSpan : SyncSpan {
    override fun setAttribute(key: String, value: String) = Unit
    override fun setAttribute(key: String, value: Long) = Unit
    override fun recordException(error: Throwable) = Unit
    override fun end(status: SyncSpanStatus) = Unit
}