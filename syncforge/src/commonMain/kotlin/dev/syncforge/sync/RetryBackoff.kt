package dev.syncforge.sync

import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Exponential backoff for outbox push retries.
 */
object RetryBackoff {

    fun delayForAttempt(
        retryCount: Int,
        base: Duration = 1_000.milliseconds,
        max: Duration = 5.minutes,
    ): Duration {
        val multiplier = 1L shl retryCount.coerceAtMost(10)
        val delayMs = min(base.inWholeMilliseconds * multiplier, max.inWholeMilliseconds)
        return delayMs.milliseconds
    }

    fun nextRetryAtMillis(retryCount: Int, nowMillis: Long = currentTimeMillis()): Long =
        nowMillis + delayForAttempt(retryCount).inWholeMilliseconds
}