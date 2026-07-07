package dev.syncforge

import android.content.Context
import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.auth.BuiltInAuthDsl
import dev.syncforge.auth.SyncForgeAuthService
import dev.syncforge.auth.createTokenStore
import dev.syncforge.auth.initTokenStoreContext
import androidx.work.Configuration
import dev.syncforge.conflict.ConflictPolicyBuilder
import dev.syncforge.entity.EntityRegistry
import dev.syncforge.entity.EntitySyncHandler
import dev.syncforge.network.createDefaultKtorSyncTransport
import dev.syncforge.network.NetworkMonitorFactory
import dev.syncforge.network.SyncAuthProvider
import dev.syncforge.network.SyncTransport
import dev.syncforge.persistence.RoomToSqlDelightMigrator
import dev.syncforge.persistence.SyncForgePersistence
import dev.syncforge.persistence.SyncForgePersistenceFactory
import dev.syncforge.persistence.conflictStore
import dev.syncforge.persistence.outboxRepository
import dev.syncforge.sync.SyncCursorStoreFactory
import dev.syncforge.sync.SyncManager
import dev.syncforge.work.AndroidSyncWorkScheduler
import dev.syncforge.work.SyncWorkerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.time.Duration

/**
 * Android-optimized setup with sensible defaults: SQLDelight outbox + conflicts (KMP shared),
 * persisted cursor, connectivity monitoring, Ktor transport, retry, and WorkManager scheduling.
 *
 * Upgrading from Room (pre-0.6.0): pending outbox rows and conflict records are migrated
 * automatically on first launch — see [RoomToSqlDelightMigrator].
 */
fun SyncForge.android(
    context: Context,
    block: AndroidSyncForgeDsl.() -> Unit,
): SyncManager =
    AndroidSyncForgeDsl(context.applicationContext).apply(block).build()

/**
 * DSL for [SyncForge.android]. Delegates to [SyncForgeBuilder] while applying Android defaults.
 */
class AndroidSyncForgeDsl internal constructor(
    private val context: Context,
) {
    private val builder = SyncForgeBuilder()
    private var baseUrl: String? = null
    private var auth: SyncAuthProvider? = null
    private var builtInAuth: BuiltInAuthDsl.() -> Unit = {}
    private var useBuiltInAuth: Boolean = false
    private var persistence: SyncForgePersistence? = null
    private var sqlDelightDatabaseName: String = "syncforge.db"
    internal var migrateFromRoom: Boolean = true
    internal var deleteRoomDatabaseAfterMigration: Boolean = true

    fun baseUrl(url: String) {
        baseUrl = url
    }

    fun authToken(provider: () -> String?) {
        auth = SyncAuthProvider.bearer(provider)
    }

    fun auth(provider: SyncAuthProvider) {
        auth = provider
    }

    /**
     * Built-in register/login/refresh against your backend. Wires [RefreshingSyncAuthProvider]
     * automatically and adds [SyncManager.register]/[SyncManager.login]/[SyncManager.logout].
     */
    @ExperimentalSyncForgeApi
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

    /**
     * SQLDelight database file name (default `syncforge.db`). Ignored when [persistence] is set.
     */
    fun databaseName(name: String) {
        require(name.isNotBlank()) { "databaseName must not be blank" }
        sqlDelightDatabaseName = name
    }

    /**
     * Inject a custom SQLDelight [SyncForgePersistence] instance.
     */
    @ExperimentalSyncForgeApi
    fun persistence(persistence: SyncForgePersistence) {
        this.persistence = persistence
    }

    fun schedulePeriodicSyncOnStart(enabled: Boolean = true) {
        builder.schedulePeriodicSyncOnStart = enabled
    }

    fun conflicts(block: ConflictPolicyBuilder.() -> Unit) {
        builder.conflicts(block)
    }

    /** Escape hatch for advanced overrides on the underlying [SyncForgeBuilder]. */
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

        val stores = persistence ?: SyncForgePersistenceFactory.create(context, sqlDelightDatabaseName)
        if (migrateFromRoom) {
            RoomToSqlDelightMigrator.migrateIfNeeded(
                context = context,
                persistence = stores,
                deleteRoomDatabaseAfterMigration = deleteRoomDatabaseAfterMigration,
            )
        }
        builder.outbox = builder.outbox
            ?: stores.outboxRepository(maxRetries = builder.maxRetries)
        builder.conflictStore = builder.conflictStore
            ?: stores.conflictStore()

        val resolvedBaseUrl = requireNotNull(baseUrl) { "baseUrl is required — e.g. baseUrl(\"https://api.example.com\")" }

        if (useBuiltInAuth) {
            initTokenStoreContext(context)
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
            ?: createDefaultKtorSyncTransport(resolvedBaseUrl, auth)
        builder.cursorStore = builder.cursorStore ?: SyncCursorStoreFactory.create(context)
        builder.networkMonitor = builder.networkMonitor ?: NetworkMonitorFactory.create(context)
        builder.workScheduler = builder.workScheduler ?: AndroidSyncWorkScheduler(context)
        builder.scope = builder.scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

        return builder.build().also { manager ->
            if (builder.schedulePeriodicSyncOnStart) {
                manager.schedulePeriodicSync()
            }
        }
    }
}

object SyncForgeAndroid {

    /**
     * WorkManager [Configuration] wired to a lazily-provided [SyncManager].
     * Use from [android.app.Application] implementing [Configuration.Provider].
     */
    fun workManagerConfiguration(syncManagerProvider: () -> SyncManager): Configuration =
        Configuration.Builder()
            .setWorkerFactory(SyncWorkerFactory(syncManagerProvider))
            .build()
}