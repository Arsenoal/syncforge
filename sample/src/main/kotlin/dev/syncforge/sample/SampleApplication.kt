package dev.syncforge.sample

import android.app.Application
import androidx.work.Configuration
import dev.syncforge.SyncForge
import dev.syncforge.SyncForgeAndroid
import dev.syncforge.android
import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.sample.notes.SyncForgeHandlers
import dev.syncforge.sample.notes.NoteRepository
import dev.syncforge.sample.tags.TagRepository
import dev.syncforge.sample.tasks.SampleDatabase
import dev.syncforge.sample.tasks.TaskRepository
import dev.syncforge.sync.SyncManager
import kotlinx.coroutines.runBlocking
import androidx.core.content.edit

class SampleApplication : Application(), Configuration.Provider {

    private lateinit var database: SampleDatabase

    lateinit var syncManager: SyncManager
        private set

    lateinit var taskRepository: TaskRepository
        private set

    lateinit var noteRepository: NoteRepository
        private set

    lateinit var tagRepository: TagRepository
        private set

    override val workManagerConfiguration: Configuration
        get() = SyncForgeAndroid.workManagerConfiguration { syncManager }

    override fun onCreate() {
        super.onCreate()

        database = SampleDatabase.create(this)
        val taskDao = database.taskDao()
        val noteDao = database.noteDao()
        val tagDao = database.tagDao()

        syncManager = SyncForge.android(this) {
            baseUrl(BuildConfig.SYNC_BASE_URL)
            registry(SyncForgeHandlers.registry(noteDao, tagDao, taskDao))
            pullPageSize = 50
            conflicts {
                entity("tasks") { deferToUser() }
                entity("notes") { lastWriteWins() }
                entity("tags") { lastWriteWins() }
            }
            schedulePeriodicSyncOnStart()
        }

        taskRepository = TaskRepository(taskDao, syncManager)
        noteRepository = NoteRepository(noteDao, syncManager)
        tagRepository = TagRepository(tagDao, syncManager)
    }

    /** Clears local sample entities and pending outbox rows between instrumented E2E tests. */
    @OptIn(ExperimentalSyncForgeApi::class)
    fun resetForE2eTests() {
        runBlocking {
            database.clearAllTables()
            syncManager.debug.clearOutbox()
            syncManager.debug.clearEventLog()
        }
        getSharedPreferences("syncforge_sync_cursor", MODE_PRIVATE).edit { clear() }
    }
}