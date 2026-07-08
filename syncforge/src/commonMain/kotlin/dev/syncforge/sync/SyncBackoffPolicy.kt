package dev.syncforge.sync

import kotlin.math.max
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Configurable delay between push retries and optional client-side sync throttling companion.
 *
 * When the server sends `Retry-After`, [nextRetryAtMillis] uses `max(serverHint, policyDelay)`.
 */
data class SyncBackoffPolicy(
    val strategy: Strategy = Strategy.EXPONENTIAL,
    val baseDelay: Duration = 1.seconds,
    val maxDelay: Duration = 5.minutes,
    /** Fraction of delay used for symmetric jitter (0 = none, 0.25 = ±25%). */
    val jitterFraction: Double = 0.0,
) {
    init {
        require(baseDelay > Duration.ZERO) { "baseDelay must be positive" }
        require(maxDelay >= baseDelay) { "maxDelay must be >= baseDelay" }
        require(jitterFraction in 0.0..1.0) { "jitterFraction must be in 0.0..1.0" }
    }

    enum class Strategy {
        /** Same delay on every attempt. */
        FIXED,
        /** `baseDelay * attemptNumber` (1-based). */
        LINEAR,
        /** `baseDelay * 2^attempt` capped at [maxDelay] (default). */
        EXPONENTIAL,
    }

    fun delayForAttempt(retryCount: Int): Duration {
        val attempt = retryCount.coerceAtLeast(0)
        val rawMs = when (strategy) {
            Strategy.FIXED -> baseDelay.inWholeMilliseconds
            Strategy.LINEAR -> baseDelay.inWholeMilliseconds * (attempt + 1)
            Strategy.EXPONENTIAL -> {
                val multiplier = 1L shl attempt.coerceAtMost(10)
                baseDelay.inWholeMilliseconds * multiplier
            }
        }
        val cappedMs = minOf(rawMs, maxDelay.inWholeMilliseconds)
        return cappedMs.milliseconds
    }

    fun nextRetryAtMillis(
        retryCount: Int,
        nowMillis: Long = currentTimeMillis(),
        serverRetryAfterMillis: Long? = null,
    ): Long {
        val policyMs = delayForAttempt(retryCount).inWholeMilliseconds
        val delayMs = serverRetryAfterMillis?.let { max(it, policyMs) } ?: policyMs
        return nowMillis + applyJitter(delayMs)
    }

    private fun applyJitter(delayMs: Long): Long {
        if (jitterFraction <= 0.0) return delayMs
        val jitterRange = (delayMs * jitterFraction).toLong()
        if (jitterRange == 0L) return delayMs
        val offset = Random.nextLong(-jitterRange, jitterRange + 1)
        return (delayMs + offset).coerceAtLeast(0)
    }

    companion object {
        val Default = SyncBackoffPolicy()
    }
}