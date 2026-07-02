package dev.syncforge

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.conflict.ConflictPolicyBuilder
import dev.syncforge.entity.EntityRegistry
import dev.syncforge.entity.EntitySyncHandler
import dev.syncforge.network.AlwaysOnlineNetworkMonitor
import dev.syncforge.network.KtorSyncTransport
import dev.syncforge.network.NetworkMonitor
import dev.syncforge.network.NetworkMonitorFactory
import dev.syncforge.network.SyncAuthProvider
import dev.syncforge.network.SyncTransport
import dev.syncforge.persistence.SyncForgePersistence
import dev.syncforge.persistence.conflictStore
import dev.syncforge.persistence.createDefaultSyncForgePersistence
import dev.syncforge.persistence.outboxRepository
import dev.syncforge.sync.NoOpSyncWorkScheduler
import dev.syncforge.sync.SyncCursorStore
import dev.syncforge.sync.SyncCursorStoreFactory
import dev.syncforge.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.time.Duration

/**
 * iOS setup with SQLDelight persistence, UserDefaults cursor, NWPathMonitor, and Darwin Ktor transport.
 */
@ExperimentalSyncForgeApi
fun SyncForge.ios(block: IosSyncForgeDsl.() -> Unit): SyncManager =
    IosSyncForgeDsl().apply(block).build()

/**
 * DSL for [SyncForge.ios]. Delegates to [SyncForgeBuilder] with iOS-appropriate defaults.
 */
@ExperimentalSyncForgeApi
class IosSyncForgeDsl internal constructor() {
    private val builder = SyncForgeBuilder()
    private var baseUrl: String? = null
    private var auth: SyncAuthProvider? = null
    private var persistence: SyncForgePersistence? = null

    fun baseUrl(url: String) {
        baseUrl = url
    }

    fun authToken(provider: () -> String?) {
        auth = SyncAuthProvider.bearer(provider)
    }

    fun auth(provider: SyncAuthProvider) {
        auth = provider
    }

    fun scope(scope: CoroutineScope) {
        builder.scope = scope
    }

    fun registry(registry: EntityRegistry) {
        builder.registry(registry)
    }

    fun handler(vararg handlers: EntitySyncHandler) {
        builder.handler(*handlers)
    }

    fun transport(transport: SyncTransport) {
        builder.transport = transport
    }

    @ExperimentalSyncForgeApi
    fun persistence(persistence: SyncForgePersistence) {
        this.persistence = persistence
    }

    fun cursorStore(store: SyncCursorStore) {
        builder.cursorStore = store
    }

    fun networkMonitor(monitor: NetworkMonitor) {
        builder.networkMonitor = monitor
    }

    /** Persists the pull cursor in an App Group [NSUserDefaults] suite. */
    fun cursorStoreAppGroup(suiteName: String) {
        builder.cursorStore = SyncCursorStoreFactory.create(suiteName = suiteName)
    }

    /** Forces always-online behavior — useful for simulator tests without path changes. */
    fun networkMonitorAlwaysOnline() {
        builder.networkMonitor = AlwaysOnlineNetworkMonitor
    }

    fun conflicts(block: ConflictPolicyBuilder.() -> Unit) {
        builder.conflicts(block)
    }

    /** Escape hatch for advanced overrides on the underlying builder. */
    fun customize(block: SyncForgeBuilder.() -> Unit) {
        builder.apply(block)
    }

    var pullPageSize: Int
        get() = builder.pullPageSize
        set(value) { builder.pullPageSize = value }

    var pushBatchSize: Int
        get() = builder.pushBatchSize
        set(value) { builder.pushBatchSize = value }

    var maxRetries: Int
        get() = builder.maxRetries
        set(value) { builder.maxRetries = value }

    var periodicSyncInterval: Duration
        get() = builder.periodicSyncInterval
        set(value) { builder.periodicSyncInterval = value }

    internal fun build(): SyncManager {
        builder.enableRetry = true

        val stores = persistence ?: createDefaultSyncForgePersistence()
        builder.outbox = builder.outbox ?: stores.outboxRepository(maxRetries = builder.maxRetries)
        builder.conflictStore = builder.conflictStore ?: stores.conflictStore()

        builder.transport = builder.transport
            ?: KtorSyncTransport(
                requireNotNull(baseUrl) { "baseUrl is required — e.g. baseUrl(\"https://api.example.com\")" },
                auth,
            )
        builder.cursorStore = builder.cursorStore ?: SyncCursorStoreFactory.create()
        builder.networkMonitor = builder.networkMonitor ?: NetworkMonitorFactory.create()
        builder.workScheduler = builder.workScheduler ?: NoOpSyncWorkScheduler
        builder.scope = builder.scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

        return builder.build()
    }
}