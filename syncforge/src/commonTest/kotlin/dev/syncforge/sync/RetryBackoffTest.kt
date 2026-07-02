package dev.syncforge.sync

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class RetryBackoffTest {

    @Test
    fun delay_growsExponentially_andIsCapped() {
        val first = RetryBackoff.delayForAttempt(0)
        val second = RetryBackoff.delayForAttempt(1)
        val capped = RetryBackoff.delayForAttempt(20)

        assertTrue(second > first)
        assertTrue(capped <= 5.minutes)
    }
}