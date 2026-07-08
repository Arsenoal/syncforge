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
import dev.syncforge.sample.demo.DemoActivityLog
import dev.syncforge.sample.conflicts.SampleConflictPolicyStore
import dev.syncforge.sample.conflicts.SampleEntityConflictKinds
import dev.syncforge.sample.conflicts.conflictPolicyFromSampleKinds
import dev.syncforge.sample.conflicts.sampleEntityConflicts
import dev.syncforge.sample.tasks.SampleDatabase
import dev.syncforge.sample.tasks.TaskRepository
import dev.syncforge.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSyncForgeApi::class)
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

    lateinit var conflictPolicyStore: SampleConflictPolicyStore
        private set

    override val workManagerConfiguration: Configuration
        get() = SyncForgeAndroid.workManagerConfiguration { syncManager }

    override fun onCreate() {
        super.onCreate()

        database = SampleDatabase.create(this)
        val taskDao = database.taskDao()
        val noteDao = database.noteDao()
        val tagDao = database.tagDao()

        conflictPolicyStore = SampleConflictPolicyStore(this)

        syncManager = SyncForge.android(this) {
            baseUrl(BuildConfig.SYNC_BASE_URL)
            registry(SyncForgeHandlers.registry(noteDao, tagDao, taskDao))
            pullPageSize = 50
            conflicts {
                sampleEntityConflicts()
            }
            schedulePeriodicSyncOnStart()
        }

        runBlocking {
            val kinds = conflictPolicyStore.currentKinds()
            syncManager.updateConflictPolicy(conflictPolicyFromSampleKinds(kinds))
        }

        taskRepository = TaskRepository(taskDao, syncManager)
        noteRepository = NoteRepository(noteDao, syncManager)
        tagRepository = TagRepository(tagDao, syncManager)
    }

    suspend fun countTasks(): Int = database.taskDao().observeAll().first().size

    /** Demo: wipe Room + SyncForge cursor/outbox to simulate fresh installation with server data intact. */
    @OptIn(ExperimentalSyncForgeApi::class)
    suspend fun resetForDemoPresentation() {
        DemoActivityLog.log(
            "Clearing local Room DB + sync cursor + outbox (server/mock-server unchanged)",
            highlight = true,
        )
        withContext(Dispatchers.IO) {
            database.clearAllTables()
            syncManager.debug.clearOutbox()
            syncManager.debug.clearEventLog()
            syncManager.debug.resetPullCursor()
        }
        DemoActivityLog.log(
            "Local DB empty — tap Sync to PULL tasks from mock-server into Room",
            highlight = true,
        )
    }

    /** Clears local sample entities and pending outbox rows between instrumented E2E tests. */
    @OptIn(ExperimentalSyncForgeApi::class)
    fun resetForE2eTests() {
        runBlocking {
            syncManager.cancelScheduledSync()
            DemoActivityLog.clear()
            conflictPolicyStore.resetToDefaults()
            syncManager.updateConflictPolicy(conflictPolicyFromSampleKinds(SampleEntityConflictKinds.Default))
            database.clearAllTables()
            syncManager.debug.clearOutbox()
            syncManager.debug.clearEventLog()
            syncManager.debug.resetPullCursor()
        }
    }
}