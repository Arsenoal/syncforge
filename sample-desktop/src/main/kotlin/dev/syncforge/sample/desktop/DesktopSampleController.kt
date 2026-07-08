package dev.syncforge.sample.desktop

import dev.syncforge.SyncForge
import dev.syncforge.desktop
import dev.syncforge.entity.EntityRegistry
import dev.syncforge.model.Change
import dev.syncforge.model.SyncResult
import dev.syncforge.model.SyncState
import dev.syncforge.network.ensureSyncForgeNetworkKtorLoaded
import dev.syncforge.sample.ios.InMemoryNoteStore
import dev.syncforge.sample.ios.InMemoryTagStore
import dev.syncforge.sample.ios.InMemoryTaskStore
import dev.syncforge.sample.ios.SampleNoteEntity
import dev.syncforge.sample.ios.SampleNoteSyncHandler
import dev.syncforge.sample.ios.SampleTagEntity
import dev.syncforge.sample.ios.SampleTagSyncHandler
import dev.syncforge.sample.ios.SampleTaskEntity
import dev.syncforge.sample.ios.SampleTaskSyncHandler
import dev.syncforge.sync.SyncCursorStoreFactory
import dev.syncforge.sync.SyncManager
import java.io.File

/**
 * JVM desktop sample — mirrors [:sample-ios-shared] [dev.syncforge.sample.ios.IosSampleController]
 * using [SyncForge.desktop].
 */
class DesktopSampleController(
    baseUrl: String = resolveBaseUrl(),
    dataDirectory: File = defaultDataDirectory(),
) {
    private val taskStore = InMemoryTaskStore()
    private val noteStore = InMemoryNoteStore()
    private val tagStore = InMemoryTagStore()
    private val taskHandler = SampleTaskSyncHandler(taskStore)
    private val noteHandler = SampleNoteSyncHandler(noteStore)
    private val tagHandler = SampleTagSyncHandler(tagStore)

    val syncManager: SyncManager = run {
        ensureSyncForgeNetworkKtorLoaded()
        dataDirectory.mkdirs()
        val databaseName = "sample-desktop-${dataDirectory.name}.db"
        SyncForge.desktop {
            baseUrl(baseUrl)
            registry(EntityRegistry.of(taskHandler, noteHandler, tagHandler))
            networkMonitorAlwaysOnline()
            databaseName(databaseName)
            cursorStore(SyncCursorStoreFactory.create(directory = dataDirectory))
            conflicts {
                entity(SampleTaskEntity.ENTITY_TYPE) { deferToUser() }
                entity(SampleNoteEntity.ENTITY_TYPE) { lastWriteWins() }
                entity(SampleTagEntity.ENTITY_TYPE) { lastWriteWins() }
            }
        }
    }

    suspend fun addTask(title: String): SampleTaskEntity {
        require(title.isNotBlank()) { "Title must not be blank" }
        val task = taskStore.createTask(title, System.currentTimeMillis())
        syncManager.enqueueChange(Change.create(SampleTaskEntity.ENTITY_TYPE, task))
        return task
    }

    suspend fun addNote(title: String, body: String = ""): SampleNoteEntity {
        require(title.isNotBlank()) { "Title must not be blank" }
        val note = noteStore.createNote(title, body, System.currentTimeMillis())
        syncManager.enqueueChange(Change.create(SampleNoteEntity.ENTITY_TYPE, note))
        return note
    }

    suspend fun sync(): SyncResult = syncManager.sync()

    fun tasks(): List<SampleTaskEntity> = taskStore.snapshotAll()

    fun taskById(id: String): SampleTaskEntity? = tasks().firstOrNull { it.id == id }

    fun noteById(id: String): SampleNoteEntity? = noteStore.snapshotAll().firstOrNull { it.id == id }

    companion object {
        const val DEFAULT_BASE_URL: String = "http://127.0.0.1:8080"

        fun resolveBaseUrl(): String =
            System.getenv("MOCK_SERVER_BASE_URL")?.trim()?.takeIf { it.isNotEmpty() }
                ?: System.getProperty("mockServerBaseUrl")?.trim()?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_BASE_URL

        fun defaultDataDirectory(): File =
            File(System.getProperty("java.io.tmpdir"), "syncforge-sample-desktop")
    }
}

fun SyncResult.isSuccessful(): Boolean = this is SyncResult.Success || this is SyncResult.Partial

fun SampleTaskEntity.isSynced(): Boolean = syncState == SyncState.SYNCED

fun SampleNoteEntity.isSynced(): Boolean = syncState == SyncState.SYNCED