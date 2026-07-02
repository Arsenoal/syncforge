package dev.syncforge.sync

import kotlin.time.Duration

/**
 * Schedules a future push retry after backoff elapses.
 */
interface RetryScheduler {
    fun scheduleRetry(delay: Duration)
    fun cancel()
}

object NoOpRetryScheduler : RetryScheduler {
    override fun scheduleRetry(delay: Duration) = Unit
    override fun cancel() = Unit
}