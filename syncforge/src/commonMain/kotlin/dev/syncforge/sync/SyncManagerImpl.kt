package dev.syncforge.sync

import dev.syncforge.auth.AuthResult
import dev.syncforge.auth.AuthState
import dev.syncforge.auth.Session
import dev.syncforge.auth.SyncForgeAuthService
import dev.syncforge.conflict.ConflictChoice
import dev.syncforge.conflict.ConflictPolicy
import dev.syncforge.conflict.ConflictPullApplier
import dev.syncforge.conflict.MutableConflictPolicy
import dev.syncforge.conflict.ConflictResolutionService
import dev.syncforge.conflict.ConflictStore
import dev.syncforge.conflict.ConflictSummary
import dev.syncforge.conflict.MergeBaseRecorder
import dev.syncforge.conflict.MergeBaseStore
import dev.syncforge.conflict.NoOpConflictStore
import dev.syncforge.conflict.NoOpMergeBaseStore
import dev.syncforge.conflict.toSummary
import dev.syncforge.debug.SyncDebug
import dev.syncforge.debug.SyncDebugImpl
import dev.syncforge.debug.SyncEventLog
import dev.syncforge.debug.SyncEventType
import dev.syncforge.entity.EntityRegistry
import dev.syncforge.model.Change
import dev.syncforge.model.SyncResult
import dev.syncforge.model.SyncStatus
import dev.syncforge.network.NetworkMonitor
import dev.syncforge.network.AlwaysOnlineNetworkMonitor
import dev.syncforge.network.SyncTransport
import dev.syncforge.trace.NoOpSyncTracer
import dev.syncforge.trace.SyncSpanName
import dev.syncforge.trace.SyncTraceAttributes
import dev.syncforge.trace.SyncTracer
import dev.syncforge.trace.recordSyncResult
import dev.syncforge.trace.runSpan
import dev.syncforge.trace.runSuspendSpan
import dev.syncforge.trace.syncSpanStatusFor
import dev.syncforge.outbox.OutboxRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds

/**
 * Default [SyncManager] implementation.
 */
internal class SyncManagerImpl(
    private val config: SyncConfig,
    private val outbox: OutboxRepository,
    private val transport: SyncTransport,
    private val registry: EntityRegistry,
    private val cursorStore: SyncCursorStore = InMemorySyncCursorStore,
    private val networkMonitor: NetworkMonitor = AlwaysOnlineNetworkMonitor,
    private val retryScheduler: RetryScheduler = NoOpRetryScheduler,
    private val workScheduler: SyncWorkScheduler = NoOpSyncWorkScheduler,
    conflictPolicy: ConflictPolicy = ConflictPolicy.Default,
    private val conflictStore: ConflictStore = NoOpConflictStore,
    private val mergeBaseStore: MergeBaseStore = NoOpMergeBaseStore,
    private val scope: CoroutineScope,
    private val authService: SyncForgeAuthService? = null,
    private val tracer: SyncTracer = NoOpSyncTracer,
) : SyncManager {

    private val loggedOutAuthState = MutableStateFlow<AuthState>(AuthState.LoggedOut)
    private val mutableConflictPolicy = MutableConflictPolicy(conflictPolicy)
    private val mergeBaseRecorder = MergeBaseRecorder(mergeBaseStore)
    private val optimisticCoordinator = OptimisticSyncCoordinator(config, registry, outbox)
    private val outboxReconciler = OutboxReconciler(outbox, optimisticCoordinator)
    private val conflictPullApplier = ConflictPullApplier(
        policy = mutableConflictPolicy,
        conflictStore = conflictStore,
        mergeBaseRecorder = mergeBaseRecorder,
        outboxReconciler = outboxReconciler,
        tracer = tracer,
    )

    private val engine: SyncEngine = SyncEngine(
        config = config,
        outbox = outbox,
        transport = transport,
        registry = registry,
        conflictStore = conflictStore,
        mergeBaseStore = mergeBaseStore,
        conflictPullApplier = conflictPullApplier,
        tracer = tracer,
    )
    private val conflictResolutionService = ConflictResolutionService(
        registry = registry,
        conflictStore = conflictStore,
        conflictApplier = conflictPullApplier,
    )
    private val eventLog = SyncEventLog(clock = ::currentTimeMillis)
    private val syncMetrics = dev.syncforge.debug.SyncMetricsCollector()

    private val mutex = Mutex()
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val status: StateFlow<SyncStatus> = _status.asStateFlow()

    override val authState: StateFlow<AuthState> =
        authService?.authState ?: loggedOutAuthState.asStateFlow()

    override val session: Session? get() = authService?.session

    override suspend fun register(fields: Map<String, String>): AuthResult {
        val service = authService ?: return authNotConfigured()
        val result = service.register(fields)
        if (result is AuthResult.Success && service.config.syncAfterRegister) {
            sync()
        }
        return result
    }

    override suspend fun login(fields: Map<String, String>): AuthResult {
        val service = authService ?: return authNotConfigured()
        val result = service.login(fields)
        if (result is AuthResult.Success && service.config.syncAfterLogin) {
            sync()
        }
        return result
    }

    override suspend fun register(email: String, password: CharArray): AuthResult {
        val service = authService ?: return authNotConfigured()
        val result = service.register(email, password)
        if (result is AuthResult.Success && service.config.syncAfterRegister) {
            sync()
        }
        return result
    }

    override suspend fun login(email: String, password: CharArray): AuthResult {
        val service = authService ?: return authNotConfigured()
        val result = service.login(email, password)
        if (result is AuthResult.Success && service.config.syncAfterLogin) {
            sync()
        }
        return result
    }

    override suspend fun logout(): AuthResult {
        val service = authService ?: return authNotConfigured()
        cancelScheduledSync()
        return service.logout()
    }

    private fun authNotConfigured(): AuthResult.Failure =
        AuthResult.Failure(
            dev.syncforge.auth.AuthError(
                code = dev.syncforge.auth.AuthError.Code.UNKNOWN,
                message = "Built-in auth not configured — add auth { } to SyncForge.android/ios/desktop",
            ),
        )

    private val _pullCursor = MutableStateFlow(cursorStore.get())
    private val syncDebugImpl = SyncDebugImpl(
        outbox = outbox,
        conflictStore = conflictStore,
        networkMonitor = networkMonitor,
        config = config,
        eventLog = eventLog,
        metrics = syncMetrics,
        status = status,
        pullCursor = _pullCursor,
        onResetPullCursor = { resetPullCursorToOrigin() },
        scope = scope,
    )
    override val debug: SyncDebug = syncDebugImpl

    override val conflicts: StateFlow<List<ConflictSummary>> =
        conflictStore.observeOpen()
            .map { records -> records.map { it.toSummary() } }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    override val conflictHistory: StateFlow<List<ConflictSummary>> =
        conflictStore.observeAll()
            .map { records -> records.map { it.toSummary() } }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var lastSyncCursor: Long = cursorStore.get()

    init {
        _pullCursor.value = lastSyncCursor

        outbox.observePendingCount()
            .onEach {
                if (_status.value !is SyncStatus.Syncing) {
                    refreshStatus()
                }
            }
            .launchIn(scope)

        conflictStore.observeOpen()
            .onEach {
                if (_status.value !is SyncStatus.Syncing) {
                    refreshStatus()
                }
            }
            .launchIn(scope)

        networkMonitor.observeOnline()
            .distinctUntilChanged()
            .filter { it }
            .onEach { triggerSyncIfNeeded() }
            .launchIn(scope)
    }

    override suspend fun sync(): SyncResult = runCycle(SyncEventType.FULL_SYNC) {
        engine.runFullSync(lastSyncCursor).also(::advanceCursorFromResult)
    }

    override suspend fun push(): SyncResult = runCycle(SyncEventType.PUSH) {
        engine.runPush()
    }

    override suspend fun pull(): SyncResult = runCycle(SyncEventType.PULL) {
        engine.runPull(lastSyncCursor).also(::advanceCursorFromResult)
    }

    override suspend fun enqueueChange(change: Change<*>) {
        optimisticCoordinator.enqueue(change)
        eventLog.record(
            type = SyncEventType.ENQUEUE,
            success = true,
            summary = "${change.type} ${change.entityType}/${change.entityId}",
        )
    }

    override suspend fun resolveConflict(
        entityType: String,
        entityId: String,
        choice: ConflictChoice,
    ) {
        mutex.withLock {
            tracer.runSpan(
                name = SyncSpanName.CONFLICT,
                attributes = mapOf(
                    SyncTraceAttributes.ENTITY_TYPE to entityType,
                    SyncTraceAttributes.ENTITY_ID to entityId,
                    SyncTraceAttributes.CONFLICT_OUTCOME to "user_resolved",
                    SyncTraceAttributes.OPERATION to choice.toString(),
                ),
            ) { }
            conflictResolutionService.resolve(entityType, entityId, choice)
            eventLog.record(
                type = SyncEventType.CONFLICT_RESOLVED,
                success = true,
                summary = "Resolved $entityType/$entityId · $choice",
            )
            refreshStatus()
        }
    }

    override fun updateConflictPolicy(policy: ConflictPolicy) {
        mutableConflictPolicy.update(policy)
    }

    override suspend fun findOpenConflict(
        entityType: String,
        entityId: String,
    ): dev.syncforge.conflict.ConflictRecord? =
        conflictStore.findOpen(entityType, entityId)

    override fun schedulePeriodicSync() {
        workScheduler.schedulePeriodic(config.periodicSyncInterval)
    }

    override fun cancelScheduledSync() {
        workScheduler.cancel()
        retryScheduler.cancel()
    }

    private suspend fun triggerSyncIfNeeded() {
        if (!networkMonitor.isOnline) return
        val ready = outbox.peek(1, currentTimeMillis())
        if (ready.isNotEmpty()) {
            push()
        }
    }

    private suspend fun resetPullCursorToOrigin() {
        lastSyncCursor = 0L
        cursorStore.set(0L)
        _pullCursor.value = 0L
        refreshStatus()
    }

    private fun advanceCursorFromResult(result: SyncResult) {
        val cursor = when (result) {
            is SyncResult.Success -> result.syncCursorMillis
            is SyncResult.Partial -> result.success.syncCursorMillis
            is SyncResult.Failure -> null
        } ?: return

        if (cursor > 0L) {
            lastSyncCursor = cursor
            cursorStore.set(cursor)
            _pullCursor.value = cursor
        }
    }

    private suspend fun scheduleRetryIfNeeded() {
        val nextAt = outbox.earliestRetryAtMillis(config.maxRetries) ?: return
        val delayMs = (nextAt - currentTimeMillis()).coerceAtLeast(0)
        tracer.runSpan(
            name = SyncSpanName.RETRY,
            attributes = mapOf(
                SyncTraceAttributes.OPERATION to "schedule",
                SyncTraceAttributes.RETRY_DELAY_MS to delayMs.toString(),
            ),
        ) { }
        retryScheduler.scheduleRetry(delayMs.milliseconds)
    }

    private suspend fun refreshStatus() {
        _status.value = engine.resolveStatus(
            networkOnline = networkMonitor.isOnline,
            lastSyncedAt = lastSyncCursor.takeIf { it > 0 },
            openConflictCount = conflictStore.countOpen(),
        )
    }

    private suspend fun runCycle(
        eventType: SyncEventType,
        block: suspend () -> SyncResult,
    ): SyncResult {
        val runBlock: suspend () -> SyncResult = {
            mutex.withLock {
            val startedAt = currentTimeMillis()
            authService?.requireAuthenticated()?.let { authFailure ->
                val failure = authFailure.toSyncFailure()
                syncDebugImpl.recordSyncResult(eventType, failure, currentTimeMillis() - startedAt)
                refreshStatus()
                return@withLock failure
            }

            if (config.requireNetwork && !networkMonitor.isOnline) {
                refreshStatus()
                val failure = SyncResult.Failure(
                    dev.syncforge.model.SyncError(
                        code = dev.syncforge.model.SyncError.Code.NETWORK,
                        message = "No network connection",
                    ),
                )
                syncDebugImpl.recordSyncResult(eventType, failure, currentTimeMillis() - startedAt)
                return@withLock failure
            }

            _status.value = SyncStatus.Syncing(
                phase = when (eventType) {
                    SyncEventType.PUSH -> SyncStatus.Syncing.Phase.PUSH
                    SyncEventType.PULL -> SyncStatus.Syncing.Phase.PULL
                    else -> SyncStatus.Syncing.Phase.FULL
                },
            )
            val result = try {
                block()
            } catch (e: Exception) {
                SyncResult.Failure(
                    dev.syncforge.model.SyncError(
                        code = dev.syncforge.model.SyncError.Code.UNKNOWN,
                        message = e.message ?: "Sync failed",
                        cause = e,
                    ),
                )
            }

            syncDebugImpl.recordSyncResult(eventType, result, currentTimeMillis() - startedAt)
            refreshStatus()
            scheduleRetryIfNeeded()
            result
            }
        }

        return if (eventType == SyncEventType.FULL_SYNC) {
            tracer.runSuspendSpan(
                name = SyncSpanName.SYNC,
                attributes = mapOf(SyncTraceAttributes.OPERATION to "full_sync"),
                statusFor = ::syncSpanStatusFor,
            ) { span ->
                runBlock().also { span.recordSyncResult(it) }
            }
        } else {
            runBlock()
        }
    }

    private fun AuthResult.Failure.toSyncFailure(): SyncResult.Failure =
        SyncResult.Failure(
            dev.syncforge.model.SyncError(
                code = dev.syncforge.model.SyncError.Code.AUTH,
                message = error.message,
                httpStatus = error.httpStatus,
            ),
        )
}

/** Platform hook for background sync scheduling. */
interface SyncWorkScheduler {
    fun schedulePeriodic(interval: kotlin.time.Duration)
    fun scheduleRetry(delay: kotlin.time.Duration)
    fun cancel()
}

object NoOpSyncWorkScheduler : SyncWorkScheduler {
    override fun schedulePeriodic(interval: kotlin.time.Duration) = Unit
    override fun scheduleRetry(delay: kotlin.time.Duration) = Unit
    override fun cancel() = Unit
}