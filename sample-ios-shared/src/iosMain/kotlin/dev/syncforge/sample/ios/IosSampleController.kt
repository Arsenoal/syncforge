package dev.syncforge.sample.ios

import co.touchlab.skie.configuration.annotations.FlowInterop
import co.touchlab.skie.configuration.annotations.SuspendInterop
import dev.syncforge.SyncForge
import dev.syncforge.compose.toUiModel
import dev.syncforge.entity.EntityRegistry
import dev.syncforge.ios
import dev.syncforge.model.Change
import dev.syncforge.model.SyncResult
import dev.syncforge.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import platform.Foundation.NSDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Swift-friendly facade for the SyncForge iOS sample.
 *
 * Callback APIs remain for UIKit/SwiftUI without async/await wiring.
 * [syncManager] and [observeStatusLabel] expose SKIE-improved Flow/suspend interop.
 */
@FlowInterop.Enabled
@SuspendInterop.Enabled
class IosSampleController(
    baseUrl: String = IOS_SAMPLE_DEFAULT_BASE_URL,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val taskStore = InMemoryTaskStore()
    private val taskHandler = SampleTaskSyncHandler(taskStore)

    val syncManager: SyncManager = SyncForge.ios {
        baseUrl(baseUrl)
        registry(EntityRegistry.of(taskHandler))
        conflicts {
            entity(SampleTaskEntity.ENTITY_TYPE) { deferToUser() }
        }
    }

    private var statusListener: ((String) -> Unit)? = null
    private var tasksListener: ((List<TaskItem>) -> Unit)? = null

    init {
        syncManager.status
            .onEach { status -> statusListener?.invoke(status.toUiModel().label) }
            .launchIn(scope)

        taskStore.observeAll()
            .onEach { tasks -> tasksListener?.invoke(tasks.map { it.toTaskItem() }) }
            .launchIn(scope)
    }

    fun setStatusListener(listener: ((String) -> Unit)?) {
        statusListener = listener
        listener?.invoke(syncManager.status.value.toUiModel().label)
    }

    fun setTasksListener(listener: ((List<TaskItem>) -> Unit)?) {
        tasksListener = listener
        listener?.invoke(taskStore.snapshotAll().map { it.toTaskItem() })
    }

    fun currentStatusLabel(): String = syncManager.status.value.toUiModel().label

    /** SKIE exports this Flow to Swift `AsyncSequence` for status observation. */
    fun observeStatusLabel() = syncManager.status.map { it.toUiModel().label }

    fun addTask(title: String, onComplete: (Boolean, String?) -> Unit) {
        if (title.isBlank()) {
            onComplete(false, "Title must not be blank")
            return
        }
        scope.launch {
            runCatching {
                val task = taskStore.createTask(title, iosNowMillis())
                syncManager.enqueueChange(Change.create(SampleTaskEntity.ENTITY_TYPE, task))
            }.fold(
                onSuccess = { onComplete(true, null) },
                onFailure = { error -> onComplete(false, error.message) },
            )
        }
    }

    fun sync(onComplete: (Boolean, String) -> Unit) {
        scope.launch {
            val result = syncManager.sync()
            val success = result is SyncResult.Success || result is SyncResult.Partial
            onComplete(success, syncManager.status.value.toUiModel().label)
        }
    }

    companion object {
        /** iOS Simulator → host machine running `:mock-server`. */
        const val DEFAULT_BASE_URL: String = "http://localhost:8080"
    }
}

private fun iosNowMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()