package dev.syncforge.sync

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Runtime configuration for [SyncManager].
 */
data class SyncConfig(
    val entityTypes: Set<String>,
    val pushBatchSize: Int = 50,
    val pullPageSize: Int = 100,
    val maxRetries: Int = 5,
    val periodicSyncInterval: Duration = 15.minutes,
    val requireNetwork: Boolean = true,
    val enableOptimisticUpdates: Boolean = true,
) {
    init {
        require(entityTypes.isNotEmpty()) { "At least one entity type must be registered" }
        require(pushBatchSize > 0) { "pushBatchSize must be positive" }
    }
}