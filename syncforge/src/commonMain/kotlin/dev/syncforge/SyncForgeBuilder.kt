package dev.syncforge

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.auth.SyncForgeAuthService
import dev.syncforge.conflict.ConflictPolicy
import dev.syncforge.conflict.ConflictPolicyBuilder
import dev.syncforge.conflict.ConflictStore
import dev.syncforge.conflict.MergeBaseStore
import dev.syncforge.conflict.NoOpConflictStore
import dev.syncforge.conflict.NoOpMergeBaseStore
import dev.syncforge.entity.EntityRegistry
import dev.syncforge.entity.EntitySyncHandler
import dev.syncforge.network.AlwaysOnlineNetworkMonitor
import dev.syncforge.network.NetworkMonitor
import dev.syncforge.network.SyncTransport
import dev.syncforge.outbox.OutboxRepository
import dev.syncforge.sync.NoOpRetryScheduler
import dev.syncforge.sync.NoOpSyncWorkScheduler
import dev.syncforge.sync.SyncConfig
import dev.syncforge.sync.SyncCursorStore
import dev.syncforge.sync.SyncManager
import dev.syncforge.sync.SyncWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Fluent builder for [SyncManager]. Entity types are derived from registered handlers —
 * you no longer need to duplicate them in [SyncConfig].
 */
@ExperimentalSyncForgeApi
class SyncForgeBuilder {

    private var entityRegistry: EntityRegistry? = null
    private val handlers = mutableListOf<EntitySyncHandler>()

    var outbox: OutboxRepository? = null
    var transport: SyncTransport? = null
    var scope: CoroutineScope? = null
    var cursorStore: SyncCursorStore? = null
    var networkMonitor: NetworkMonitor? = null
    var workScheduler: SyncWorkScheduler? = null
    var enableRetry: Boolean = true

    var pushBatchSize: Int = 50
    var pullPageSize: Int = 100
    var maxRetries: Int = 5
    var periodicSyncInterval: Duration = 15.minutes
    var requireNetwork: Boolean = true
    var enableOptimisticUpdates: Boolean = true
    var conflictPolicy: ConflictPolicy = ConflictPolicy.Default
    var conflictStore: ConflictStore? = null
    var mergeBaseStore: MergeBaseStore? = null
    var authService: SyncForgeAuthService? = null

    internal var schedulePeriodicSyncOnStart: Boolean = false

    fun conflicts(block: ConflictPolicyBuilder.() -> Unit) {
        conflictPolicy = ConflictPolicyBuilder().apply(block).build()
    }

    fun registry(registry: EntityRegistry) {
        entityRegistry = registry
    }

    fun handler(vararg handlers: EntitySyncHandler) {
        this.handlers.addAll(handlers)
    }

    fun handler(handlers: Iterable<EntitySyncHandler>) {
        this.handlers.addAll(handlers)
    }

    fun build(): SyncManager {
        val registry = entityRegistry ?: EntityRegistry(handlers)
        val outbox = requireNotNull(outbox) { "outbox is required — use SyncForge.android() for Android defaults" }
        val transport = requireNotNull(transport) { "transport is required — set baseUrl() on Android or provide transport explicitly" }
        val scope = requireNotNull(scope) { "scope is required — use SyncForge.android() for a default application scope" }

        val config = SyncConfig(
            entityTypes = registry.entityTypes(),
            pushBatchSize = pushBatchSize,
            pullPageSize = pullPageSize,
            maxRetries = maxRetries,
            periodicSyncInterval = periodicSyncInterval,
            requireNetwork = requireNetwork,
            enableOptimisticUpdates = enableOptimisticUpdates,
        )

        return if (enableRetry) {
            SyncForge.createWithRetry(
                config = config,
                outbox = outbox,
                transport = transport,
                registry = registry,
                scope = scope,
                cursorStore = cursorStore ?: dev.syncforge.sync.InMemorySyncCursorStore,
                networkMonitor = networkMonitor ?: AlwaysOnlineNetworkMonitor,
                workScheduler = workScheduler ?: NoOpSyncWorkScheduler,
                conflictPolicy = conflictPolicy,
                conflictStore = conflictStore ?: NoOpConflictStore,
                mergeBaseStore = mergeBaseStore ?: NoOpMergeBaseStore,
                authService = authService,
            )
        } else {
            SyncForge.create(
                config = config,
                outbox = outbox,
                transport = transport,
                registry = registry,
                scope = scope,
                cursorStore = cursorStore ?: dev.syncforge.sync.InMemorySyncCursorStore,
                networkMonitor = networkMonitor ?: AlwaysOnlineNetworkMonitor,
                retryScheduler = NoOpRetryScheduler,
                workScheduler = workScheduler ?: NoOpSyncWorkScheduler,
                conflictPolicy = conflictPolicy,
                conflictStore = conflictStore ?: NoOpConflictStore,
                mergeBaseStore = mergeBaseStore ?: NoOpMergeBaseStore,
                authService = authService,
            )
        }
    }
}

/**
 * Low-level builder for custom platforms or tests. Prefer [SyncForge.android] on Android.
 */
@ExperimentalSyncForgeApi
fun SyncForge.builder(block: SyncForgeBuilder.() -> Unit): SyncManager =
    SyncForgeBuilder().apply(block).build()