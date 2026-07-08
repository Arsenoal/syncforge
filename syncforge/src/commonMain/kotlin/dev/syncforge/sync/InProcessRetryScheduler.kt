package dev.syncforge.sync

import dev.syncforge.trace.NoOpSyncTracer
import dev.syncforge.trace.SyncSpanName
import dev.syncforge.trace.SyncTraceAttributes
import dev.syncforge.trace.SyncTracer
import dev.syncforge.trace.runSuspendSpan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * Coroutine-based retry scheduler — suitable for apps with a long-lived [CoroutineScope].
 */
internal class InProcessRetryScheduler(
    private val scope: CoroutineScope,
    private val onRetry: suspend () -> Unit,
    private val tracer: SyncTracer = NoOpSyncTracer,
) : RetryScheduler {

    private var retryJob: Job? = null

    override fun scheduleRetry(delay: Duration) {
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(delay)
            tracer.runSuspendSpan(
                name = SyncSpanName.RETRY,
                attributes = mapOf(
                    SyncTraceAttributes.OPERATION to "execute",
                    SyncTraceAttributes.RETRY_DELAY_MS to delay.inWholeMilliseconds.toString(),
                ),
            ) {
                onRetry()
            }
        }
    }

    override fun cancel() {
        retryJob?.cancel()
        retryJob = null
    }
}