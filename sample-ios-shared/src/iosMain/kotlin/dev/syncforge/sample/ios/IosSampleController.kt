package dev.syncforge.sample.ios

import dev.syncforge.SyncForge
import dev.syncforge.compose.toUiModel
import dev.syncforge.entity.EntityRegistry
import dev.syncforge.ios
import dev.syncforge.model.Change
import dev.syncforge.model.SyncResult
import dev.syncforge.network.ensureSyncForgeNetworkKtorLoaded
import dev.syncforge.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Swift-friendly facade for the SyncForge iOS sample.
 *
 * Mirrors Android :sample — tasks, notes, and tags on one [SyncManager].
 *
 * SKIE Flow/Suspend interop is enabled for `dev.syncforge.sample.ios` in
 * `:sample-ios-shared` Gradle config (not via class-level annotations).
 */
class IosSampleController(
    baseUrl: String = IOS_SAMPLE_DEFAULT_BASE_URL,
    /** When true, skips BGTask periodic scheduling (XCUITest / CI). */
    e2eMode: Boolean = false,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val taskStore = InMemoryTaskStore()
    private val noteStore = InMemoryNoteStore()
    private val tagStore = InMemoryTagStore()
    private val taskHandler = SampleTaskSyncHandler(taskStore)
    private val noteHandler = SampleNoteSyncHandler(noteStore)
    private val tagHandler = SampleTagSyncHandler(tagStore)

    val syncManager: SyncManager = run {
        ensureSyncForgeNetworkKtorLoaded()
        SyncForge.ios {
            baseUrl(baseUrl)
            registry(EntityRegistry.of(taskHandler, noteHandler, tagHandler))
            backgroundSyncTaskIdentifier(IOS_SAMPLE_BACKGROUND_SYNC_TASK_ID)
            // Simulator XCUITest: NWPathMonitor may report offline before the first path update.
            networkMonitorAlwaysOnline()
            if (!e2eMode) {
                schedulePeriodicSyncOnStart()
            }
            conflicts {
                entity(SampleTaskEntity.ENTITY_TYPE) { deferToUser() }
                entity(SampleNoteEntity.ENTITY_TYPE) { lastWriteWins() }
                entity(SampleTagEntity.ENTITY_TYPE) { lastWriteWins() }
            }
        }
    }

    private var statusListener: ((String) -> Unit)? = null
    private var tasksListener: ((List<TaskItem>) -> Unit)? = null
    private var notesListener: ((List<NoteItem>) -> Unit)? = null
    private var tagsListener: ((List<TagItem>) -> Unit)? = null

    init {
        syncManager.status
            .onEach { status -> statusListener?.invoke(status.toUiModel().label) }
            .launchIn(scope)

        taskStore.observeAll()
            .onEach { tasks -> tasksListener?.invoke(tasks.map { it.toTaskItem() }) }
            .launchIn(scope)

        noteStore.observeAll()
            .onEach { notes -> notesListener?.invoke(notes.map { it.toNoteItem() }) }
            .launchIn(scope)

        tagStore.observeAll()
            .onEach { tags -> tagsListener?.invoke(tags.map { it.toTagItem() }) }
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

    fun setNotesListener(listener: ((List<NoteItem>) -> Unit)?) {
        notesListener = listener
        listener?.invoke(noteStore.snapshotAll().map { it.toNoteItem() })
    }

    fun setTagsListener(listener: ((List<TagItem>) -> Unit)?) {
        tagsListener = listener
        listener?.invoke(tagStore.snapshotAll().map { it.toTagItem() })
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

    fun addNote(title: String, body: String, onComplete: (Boolean, String?) -> Unit) {
        if (title.isBlank()) {
            onComplete(false, "Title must not be blank")
            return
        }
        scope.launch {
            runCatching {
                val note = noteStore.createNote(title, body, iosNowMillis())
                syncManager.enqueueChange(Change.create(SampleNoteEntity.ENTITY_TYPE, note))
            }.fold(
                onSuccess = { onComplete(true, null) },
                onFailure = { error -> onComplete(false, error.message) },
            )
        }
    }

    fun addTag(label: String, onComplete: (Boolean, String?) -> Unit) {
        if (label.isBlank()) {
            onComplete(false, "Label must not be blank")
            return
        }
        scope.launch {
            runCatching {
                val tag = tagStore.createTag(label, iosNowMillis())
                syncManager.enqueueChange(Change.create(SampleTagEntity.ENTITY_TYPE, tag))
            }.fold(
                onSuccess = { onComplete(true, null) },
                onFailure = { error -> onComplete(false, error.message) },
            )
        }
    }

    fun deleteNote(noteId: String, onComplete: (Boolean, String?) -> Unit) {
        scope.launch {
            runCatching {
                val note = noteStore.findById(noteId) ?: error("Note not found")
                syncManager.enqueueChange(
                    Change.delete<SampleNoteEntity>(
                        entityType = SampleNoteEntity.ENTITY_TYPE,
                        entityId = note.id,
                        localVersion = note.localVersion + 1,
                        updatedAtMillis = iosNowMillis(),
                    ),
                )
            }.fold(
                onSuccess = { onComplete(true, null) },
                onFailure = { error -> onComplete(false, error.message) },
            )
        }
    }

    fun deleteTag(tagId: String, onComplete: (Boolean, String?) -> Unit) {
        scope.launch {
            runCatching {
                val tag = tagStore.findById(tagId) ?: error("Tag not found")
                syncManager.enqueueChange(
                    Change.delete<SampleTagEntity>(
                        entityType = SampleTagEntity.ENTITY_TYPE,
                        entityId = tag.id,
                        localVersion = tag.localVersion + 1,
                        updatedAtMillis = iosNowMillis(),
                    ),
                )
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
        const val DEFAULT_BASE_URL: String = "http://127.0.0.1:8080"
    }
}

private fun iosNowMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()