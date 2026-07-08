package dev.syncforge.sample.web

import dev.syncforge.SyncForge
import dev.syncforge.entity.EntityRegistry
import dev.syncforge.model.Change
import dev.syncforge.model.SyncResult
import dev.syncforge.model.SyncState
import dev.syncforge.sample.ios.InMemoryNoteStore
import dev.syncforge.sample.ios.InMemoryTagStore
import dev.syncforge.sample.ios.InMemoryTaskStore
import dev.syncforge.sample.ios.SampleNoteEntity
import dev.syncforge.sample.ios.SampleNoteSyncHandler
import dev.syncforge.sample.ios.SampleTagEntity
import dev.syncforge.sample.ios.SampleTagSyncHandler
import dev.syncforge.sample.ios.SampleTaskEntity
import dev.syncforge.sample.ios.SampleTaskSyncHandler
import dev.syncforge.sync.SyncManager
import dev.syncforge.web

/**
 * Browser sample — mirrors [:sample-desktop] [dev.syncforge.sample.desktop.DesktopSampleController]
 * using [SyncForge.web].
 */
class WebSampleController private constructor(
    private val taskStore: InMemoryTaskStore,
    private val noteStore: InMemoryNoteStore,
    val syncManager: SyncManager,
) {
    suspend fun addTask(title: String): SampleTaskEntity {
        require(title.isNotBlank()) { "Title must not be blank" }
        val task = taskStore.createTask(title, webNowMillis())
        syncManager.enqueueChange(Change.create(SampleTaskEntity.ENTITY_TYPE, task))
        return task
    }

    suspend fun addNote(title: String, body: String = ""): SampleNoteEntity {
        require(title.isNotBlank()) { "Title must not be blank" }
        val note = noteStore.createNote(title, body, webNowMillis())
        syncManager.enqueueChange(Change.create(SampleNoteEntity.ENTITY_TYPE, note))
        return note
    }

    suspend fun sync(): SyncResult = syncManager.sync()

    fun tasks(): List<SampleTaskEntity> = taskStore.snapshotAll()

    fun taskById(id: String): SampleTaskEntity? = tasks().firstOrNull { it.id == id }

    fun noteById(id: String): SampleNoteEntity? = noteStore.snapshotAll().firstOrNull { it.id == id }

    companion object {
        const val DEFAULT_BASE_URL: String = "http://127.0.0.1:8080"

        suspend fun create(
            baseUrl: String = resolveBaseUrl(),
            databaseName: String = defaultDatabaseName(),
        ): WebSampleController {
            val taskStore = InMemoryTaskStore()
            val noteStore = InMemoryNoteStore()
            val tagStore = InMemoryTagStore()
            val taskHandler = SampleTaskSyncHandler(taskStore)
            val noteHandler = SampleNoteSyncHandler(noteStore)
            val tagHandler = SampleTagSyncHandler(tagStore)

            val syncManager = SyncForge.web {
                baseUrl(baseUrl)
                registry(EntityRegistry.of(taskHandler, noteHandler, tagHandler))
                networkMonitorAlwaysOnline()
                databaseName(databaseName)
                conflicts {
                    entity(SampleTaskEntity.ENTITY_TYPE) { deferToUser() }
                    entity(SampleNoteEntity.ENTITY_TYPE) { lastWriteWins() }
                    entity(SampleTagEntity.ENTITY_TYPE) { lastWriteWins() }
                }
            }

            return WebSampleController(taskStore, noteStore, syncManager)
        }

        fun resolveBaseUrl(): String =
            readGlobalString("MOCK_SERVER_BASE_URL") ?: DEFAULT_BASE_URL

        fun defaultDatabaseName(): String = "sample-web-${webNowMillis()}"
    }
}

fun SyncResult.isSuccessful(): Boolean = this is SyncResult.Success || this is SyncResult.Partial

fun SampleTaskEntity.isSynced(): Boolean = syncState == SyncState.SYNCED

fun SampleNoteEntity.isSynced(): Boolean = syncState == SyncState.SYNCED

private fun webNowMillis(): Long = kotlin.js.Date.now().toLong()

private fun readGlobalString(key: String): String? {
    val lookupKey = key
    val value = js(
        """(function() {
            var v = globalThis[lookupKey];
            return (typeof v === 'string' && v.length > 0) ? v : null;
        })()""",
    ) as String?
    return value?.trim()?.takeIf { it.isNotEmpty() }
}