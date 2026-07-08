package dev.syncforge.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SyncBackoffPolicyTest {

    @Test
    fun exponential_matchesLegacyRetryBackoff() {
        val policy = SyncBackoffPolicy.Default
        repeat(5) { attempt ->
            assertEquals(
                RetryBackoff.delayForAttempt(attempt),
                policy.delayForAttempt(attempt),
            )
        }
    }

    @Test
    fun linear_growsWithAttempt() {
        val policy = SyncBackoffPolicy(
            strategy = SyncBackoffPolicy.Strategy.LINEAR,
            baseDelay = 2.seconds,
            maxDelay = 1.minutes,
        )
        assertEquals(2_000.milliseconds, policy.delayForAttempt(0))
        assertEquals(4_000.milliseconds, policy.delayForAttempt(1))
        assertEquals(6_000.milliseconds, policy.delayForAttempt(2))
    }

    @Test
    fun fixed_usesSameDelay() {
        val policy = SyncBackoffPolicy(
            strategy = SyncBackoffPolicy.Strategy.FIXED,
            baseDelay = 3.seconds,
            maxDelay = 1.minutes,
        )
        assertEquals(3_000.milliseconds, policy.delayForAttempt(0))
        assertEquals(3_000.milliseconds, policy.delayForAttempt(5))
    }

    @Test
    fun serverRetryAfter_usesMaxOfPolicyAndServerHint() {
        val policy = SyncBackoffPolicy(
            strategy = SyncBackoffPolicy.Strategy.FIXED,
            baseDelay = 1.seconds,
        )
        val at = policy.nextRetryAtMillis(
            retryCount = 1,
            nowMillis = 10_000L,
            serverRetryAfterMillis = 30_000L,
        )
        assertEquals(40_000L, at)
    }

    @Test
    fun exponential_isCappedAtMaxDelay() {
        val policy = SyncBackoffPolicy(
            strategy = SyncBackoffPolicy.Strategy.EXPONENTIAL,
            baseDelay = 1.seconds,
            maxDelay = 5.minutes,
        )
        assertTrue(policy.delayForAttempt(20) <= 5.minutes)
    }
}