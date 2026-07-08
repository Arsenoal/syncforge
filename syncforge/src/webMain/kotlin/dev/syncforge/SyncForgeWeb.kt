package dev.syncforge

import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.auth.BuiltInAuthDsl
import dev.syncforge.auth.SyncForgeAuthService
import dev.syncforge.auth.createTokenStore
import dev.syncforge.conflict.ConflictPolicyBuilder
import dev.syncforge.entity.EntityRegistry
import dev.syncforge.entity.EntitySyncHandler
import dev.syncforge.network.NetworkMonitor
import dev.syncforge.network.NetworkMonitorFactory
import dev.syncforge.network.SyncAuthProvider
import dev.syncforge.network.SyncTransport
import dev.syncforge.network.createKtorSyncTransport

import dev.syncforge.persistence.SyncForgePersistence
import dev.syncforge.persistence.conflictStore
import dev.syncforge.persistence.createDefaultSyncForgePersistence
import dev.syncforge.persistence.mergeBaseStore
import dev.syncforge.persistence.outboxRepository
import dev.syncforge.sync.NoOpSyncWorkScheduler
import dev.syncforge.sync.SyncCursorStore
import dev.syncforge.sync.SyncCursorStoreFactory
import dev.syncforge.sync.SyncManager
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.time.Duration

/**
 * Experimental browser setup (1.6-01) — SQLDelight web-worker persistence, localStorage cursor,
 * and Ktor JS transport. Full `SyncForge.web { }` polish lands in 1.6-02.
 */
@ExperimentalSyncForgeApi
fun SyncForge.web(block: WebSyncForgeDsl.() -> Unit): SyncManager =
    WebSyncForgeDsl().apply(block).build()

@ExperimentalSyncForgeApi
class WebSyncForgeDsl internal constructor() {
    private val builder = SyncForgeBuilder()
    private var baseUrl: String? = null
    private var auth: SyncAuthProvider? = null
    private var builtInAuth: BuiltInAuthDsl.() -> Unit = {}
    private var useBuiltInAuth: Boolean = false
    private var persistence: SyncForgePersistence? = null
    private var httpClient: HttpClient? = null
    private var sqlDelightDatabaseName: String = "syncforge.db"

    fun baseUrl(url: String) {
        baseUrl = url
    }

    fun authToken(provider: () -> String?) {
        auth = SyncAuthProvider.bearer(provider)
    }

    fun auth(provider: SyncAuthProvider) {
        auth = provider
    }

    fun auth(block: BuiltInAuthDsl.() -> Unit) {
        useBuiltInAuth = true
        builtInAuth = block
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

    fun httpClient(client: HttpClient) {
        this.httpClient = client
    }

    fun databaseName(name: String) {
        require(name.isNotBlank()) { "databaseName must not be blank" }
        sqlDelightDatabaseName = name
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

    fun conflicts(block: ConflictPolicyBuilder.() -> Unit) {
        builder.conflicts(block)
    }

    @ExperimentalSyncForgeApi
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

        val stores = persistence ?: createDefaultSyncForgePersistence(databaseName = sqlDelightDatabaseName)
        builder.outbox = builder.outbox ?: stores.outboxRepository(maxRetries = builder.maxRetries)
        builder.conflictStore = builder.conflictStore ?: stores.conflictStore()
        builder.mergeBaseStore = builder.mergeBaseStore ?: stores.mergeBaseStore()

        val resolvedBaseUrl = requireNotNull(baseUrl) { "baseUrl is required — e.g. baseUrl(\"http://localhost:8080\")" }

        if (useBuiltInAuth) {
            val authConfig = BuiltInAuthDsl().apply(builtInAuth).build()
            val authService = SyncForgeAuthService.create(
                baseUrl = resolvedBaseUrl,
                config = authConfig,
                tokenStore = createTokenStore(),
            )
            builder.authService = authService
            auth = authService.authProvider
        }

        builder.transport = builder.transport
            ?: createKtorSyncTransport(resolvedBaseUrl, auth, httpClient)
        builder.cursorStore = builder.cursorStore ?: SyncCursorStoreFactory.create()
        builder.networkMonitor = builder.networkMonitor ?: NetworkMonitorFactory.create()
        builder.workScheduler = builder.workScheduler ?: NoOpSyncWorkScheduler
        builder.scope = builder.scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

        return builder.build()
    }
}