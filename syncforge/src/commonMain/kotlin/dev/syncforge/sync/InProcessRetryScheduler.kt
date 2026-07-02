package dev.syncforge.sync

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
) : RetryScheduler {

    private var retryJob: Job? = null

    override fun scheduleRetry(delay: Duration) {
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(delay)
            onRetry()
        }
    }

    override fun cancel() {
        retryJob?.cancel()
        retryJob = null
    }
}