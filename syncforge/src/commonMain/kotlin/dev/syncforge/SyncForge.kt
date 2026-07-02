package dev.syncforge

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.auth.SyncForgeAuthService
import dev.syncforge.conflict.ConflictPolicy
import dev.syncforge.conflict.ConflictStore
import dev.syncforge.conflict.NoOpConflictStore
import dev.syncforge.entity.EntityRegistry
import dev.syncforge.network.AlwaysOnlineNetworkMonitor
import dev.syncforge.network.NetworkMonitor
import dev.syncforge.network.SyncTransport
import dev.syncforge.outbox.OutboxRepository
import dev.syncforge.sync.InMemorySyncCursorStore
import dev.syncforge.sync.NoOpRetryScheduler
import dev.syncforge.sync.NoOpSyncWorkScheduler
import dev.syncforge.sync.RetryScheduler
import dev.syncforge.sync.SyncConfig
import dev.syncforge.sync.SyncCursorStore
import dev.syncforge.sync.SyncManager
import dev.syncforge.sync.SyncManagerImpl
import dev.syncforge.sync.SyncWorkScheduler
import kotlinx.coroutines.CoroutineScope

/**
 * Entry point for building a configured [SyncManager] instance.
 *
 * **Recommended setup (Android):**
 * ```
 * SyncForge.android(context) {
 *     baseUrl("https://api.example.com")
 *     registry(SyncForgeHandlers.registry(taskDao))
 *     schedulePeriodicSyncOnStart()
 * }
 * ```
 *
 * Low-level [create] / [createWithRetry] remain available for custom wiring.
 */
object SyncForge {

    @ExperimentalSyncForgeApi
    fun create(
        config: SyncConfig,
        outbox: OutboxRepository,
        transport: SyncTransport,
        registry: EntityRegistry,
        scope: CoroutineScope,
        cursorStore: SyncCursorStore = InMemorySyncCursorStore,
        networkMonitor: NetworkMonitor = AlwaysOnlineNetworkMonitor,
        retryScheduler: RetryScheduler = NoOpRetryScheduler,
        workScheduler: SyncWorkScheduler = NoOpSyncWorkScheduler,
        conflictPolicy: ConflictPolicy = ConflictPolicy.Default,
        conflictStore: ConflictStore = NoOpConflictStore,
        authService: SyncForgeAuthService? = null,
    ): SyncManager {
        require(config.entityTypes.containsAll(registry.entityTypes())) {
            "SyncConfig.entityTypes must include all registered handler types: " +
                "config=${config.entityTypes}, registry=${registry.entityTypes()}"
        }
        return SyncManagerImpl(
            config = config,
            outbox = outbox,
            transport = transport,
            registry = registry,
            cursorStore = cursorStore,
            networkMonitor = networkMonitor,
            retryScheduler = retryScheduler,
            workScheduler = workScheduler,
            conflictPolicy = conflictPolicy,
            conflictStore = conflictStore,
            scope = scope,
            authService = authService,
        )
    }

    /**
     * Creates [SyncManager] with an in-process retry scheduler that re-attempts push on backoff.
     */
    @ExperimentalSyncForgeApi
    fun createWithRetry(
        config: SyncConfig,
        outbox: OutboxRepository,
        transport: SyncTransport,
        registry: EntityRegistry,
        scope: CoroutineScope,
        cursorStore: SyncCursorStore = InMemorySyncCursorStore,
        networkMonitor: NetworkMonitor = AlwaysOnlineNetworkMonitor,
        workScheduler: SyncWorkScheduler = NoOpSyncWorkScheduler,
        conflictPolicy: ConflictPolicy = ConflictPolicy.Default,
        conflictStore: ConflictStore = NoOpConflictStore,
        authService: SyncForgeAuthService? = null,
    ): SyncManager {
        lateinit var manager: SyncManager
        val retryScheduler = dev.syncforge.sync.InProcessRetryScheduler(scope) {
            manager.push()
        }
        manager = create(
            config = config,
            outbox = outbox,
            transport = transport,
            registry = registry,
            scope = scope,
            cursorStore = cursorStore,
            networkMonitor = networkMonitor,
            retryScheduler = retryScheduler,
            workScheduler = workScheduler,
            conflictPolicy = conflictPolicy,
            conflictStore = conflictStore,
            authService = authService,
        )
        return manager
    }
}