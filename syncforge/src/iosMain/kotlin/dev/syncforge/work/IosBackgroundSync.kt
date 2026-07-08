package dev.syncforge.work

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateByAddingTimeInterval
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * BGTaskScheduler integration for iOS background sync.
 *
 * Call [registerBackgroundTasks] once from `UIApplicationDelegate.application(_:didFinishLaunchingWithOptions:)`
 * before returning. Bind the sync lambda via [bind] when [dev.syncforge.sync.SyncManager] is ready
 * (or use [SyncForge.ios] with `schedulePeriodicSyncOnStart()`).
 */
object IosBackgroundSync {

    const val DEFAULT_TASK_IDENTIFIER: String = DEFAULT_BACKGROUND_SYNC_TASK_IDENTIFIER

    private var taskIdentifier: String = DEFAULT_BACKGROUND_SYNC_TASK_IDENTIFIER
    private var onSync: (suspend () -> Unit)? = null
    private var periodicInterval: Duration = 15.minutes
    private var handlerRegistered: Boolean = false

    /**
     * Registers the BGAppRefreshTask handler. Must run during app launch (before returning from
     * `didFinishLaunchingWithOptions`).
     */
    @OptIn(ExperimentalForeignApi::class)
    fun registerBackgroundTasks(
        taskIdentifier: String = DEFAULT_BACKGROUND_SYNC_TASK_IDENTIFIER,
    ) {
        this.taskIdentifier = taskIdentifier
        if (handlerRegistered) return

        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
            identifier = taskIdentifier,
            usingQueue = null,
        ) { task ->
            val refreshTask = task as? BGAppRefreshTask
            refreshTask?.expirationHandler = {
                refreshTask.setTaskCompletedWithSuccess(false)
            }
            val action = onSync
            val success = if (action != null) {
                runBlocking {
                    runCatching { action.invoke() }.isSuccess
                }
            } else {
                false
            }
            refreshTask?.setTaskCompletedWithSuccess(success)
            submitAppRefresh(periodicInterval, taskIdentifier)
        }
        handlerRegistered = true
    }

    /** Binds the suspend sync action invoked when BGTaskScheduler fires. */
    fun bind(onSync: suspend () -> Unit) {
        this.onSync = onSync
    }

    internal fun scheduleAppRefresh(interval: Duration, identifier: String = taskIdentifier) {
        periodicInterval = interval.coerceAtLeast(MIN_REFRESH_INTERVAL)
        taskIdentifier = identifier
        submitAppRefresh(periodicInterval, identifier)
    }

    internal fun scheduleRetry(delay: Duration, identifier: String = taskIdentifier) {
        taskIdentifier = identifier
        submitAppRefresh(delay.coerceAtLeast(MIN_RETRY_DELAY), identifier)
    }

    internal fun cancel(@Suppress("UNUSED_PARAMETER") identifier: String = taskIdentifier) {
        BGTaskScheduler.sharedScheduler.cancelAllTaskRequests()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun submitAppRefresh(delay: Duration, identifier: String) {
        val request = BGAppRefreshTaskRequest(identifier = identifier)
        request.earliestBeginDate = NSDate().dateByAddingTimeInterval(delay.inWholeSeconds.toDouble())
        BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)
    }

    private val MIN_REFRESH_INTERVAL: Duration = 15.minutes
    private val MIN_RETRY_DELAY: Duration = 1.minutes
}

/** Registers BGTaskScheduler handlers — Swift: `IosBackgroundSyncKt.registerIosBackgroundSyncTasks(...)`. */
fun registerIosBackgroundSyncTasks(
    taskIdentifier: String = DEFAULT_BACKGROUND_SYNC_TASK_IDENTIFIER,
) {
    IosBackgroundSync.registerBackgroundTasks(taskIdentifier)
}