package dev.syncforge.sync

import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Exponential backoff for outbox push retries.
 *
 * Delegates to [SyncBackoffPolicy.Default]; prefer configuring [SyncConfig.backoffPolicy].
 */
object RetryBackoff {

    fun delayForAttempt(
        retryCount: Int,
        base: Duration = 1_000.milliseconds,
        max: Duration = 5.minutes,
    ): Duration =
        SyncBackoffPolicy(
            strategy = SyncBackoffPolicy.Strategy.EXPONENTIAL,
            baseDelay = base,
            maxDelay = max,
        ).delayForAttempt(retryCount)

    fun nextRetryAtMillis(retryCount: Int, nowMillis: Long = currentTimeMillis()): Long =
        SyncBackoffPolicy.Default.nextRetryAtMillis(retryCount, nowMillis)
}