package dev.syncforge.conflict

import dev.syncforge.entity.PullApplyOutcome
import dev.syncforge.entity.SyncedEntity
import dev.syncforge.entity.TypedEntitySyncHandler
import dev.syncforge.model.SyncState
import dev.syncforge.network.RemoteDelta
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MergeBasePullApplierTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun applyDelta_insertsMergeBaseOnRemoteInsert() = runTest {
        val store = InMemoryMergeBaseStore()
        val handler = TestTaskHandler()
        val applier = ConflictPullApplier(
            policy = ConflictPolicy.Default,
            conflictStore = NoOpConflictStore,
            mergeBaseRecorder = MergeBaseRecorder(store),
        )

        val outcome = applier.applyDelta(
            handler = handler,
            delta = RemoteDelta(
                entityType = "tasks",
                entityId = "t1",
                payloadJson = json.encodeToString(TestTask("t1", title = "remote")),
                serverVersion = 3L,
                updatedAtMillis = 300L,
            ),
        )

        assertEquals(PullApplyOutcome.INSERTED, outcome)
        val base = store.get("tasks", "t1")
        assertEquals(3L, base?.serverVersion)
        assertEquals("remote", json.decodeFromString<TestTask>(base!!.payloadJson).title)
    }

    @Test
    fun applyDelta_updatesMergeBaseOnNonConflictPull() = runTest {
        val store = InMemoryMergeBaseStore()
        val handler = TestTaskHandler(
            initial = TestTask("t1", title = "local", syncState = SyncState.SYNCED, localVersion = 2),
        )
        val applier = ConflictPullApplier(
            policy = ConflictPolicy.Default,
            conflictStore = NoOpConflictStore,
            mergeBaseRecorder = MergeBaseRecorder(store),
        )

        applier.applyDelta(
            handler = handler,
            delta = RemoteDelta(
                entityType = "tasks",
                entityId = "t1",
                payloadJson = json.encodeToString(TestTask("t1", title = "server", localVersion = 2)),
                serverVersion = 2L,
                updatedAtMillis = 400L,
            ),
        )

        val base = store.get("tasks", "t1")
        assertEquals("server", json.decodeFromString<TestTask>(base!!.payloadJson).title)
        assertEquals(2L, base.serverVersion)
    }

    @Test
    fun applyDelta_removesMergeBaseOnRemoteDelete() = runTest {
        val store = InMemoryMergeBaseStore()
        store.put(
            MergeBaseSnapshot(
                entityType = "tasks",
                entityId = "t1",
                payloadJson = "{}",
                serverVersion = 1L,
                updatedAtMillis = 1L,
                storedAtMillis = 1L,
            ),
        )
        val handler = TestTaskHandler(
            initial = TestTask("t1", syncState = SyncState.SYNCED, localVersion = 2),
        )
        val applier = ConflictPullApplier(
            policy = ConflictPolicy.Default,
            conflictStore = NoOpConflictStore,
            mergeBaseRecorder = MergeBaseRecorder(store),
        )

        applier.applyDelta(
            handler = handler,
            delta = RemoteDelta(
                entityType = "tasks",
                entityId = "t1",
                payloadJson = null,
                serverVersion = 2L,
                updatedAtMillis = 200L,
                isDeleted = true,
            ),
        )

        assertNull(store.get("tasks", "t1"))
    }

    @Serializable
    private data class TestTask(
        override val id: String,
        val title: String = "task",
        override val localVersion: Long = 0,
        override val updatedAtMillis: Long = 100L,
        override val syncState: SyncState = SyncState.SYNCED,
    ) : SyncedEntity

    private class TestTaskHandler(
        initial: TestTask? = null,
    ) : TypedEntitySyncHandler<TestTask>() {

        override val entityType: String = "tasks"
        private val entities = mutableMapOf<String, TestTask>()
        private val json = Json { ignoreUnknownKeys = true }

        init {
            if (initial != null) {
                entities[initial.id] = initial
            }
        }

        override fun toJson(entity: TestTask): String = json.encodeToString(entity)

        override fun fromJson(json: String): TestTask = this.json.decodeFromString(json)

        override suspend fun findById(id: String): TestTask? = entities[id]

        override suspend fun insert(entity: TestTask) {
            entities[entity.id] = entity
        }

        override suspend fun update(entity: TestTask) {
            entities[entity.id] = entity
        }

        override suspend fun deleteById(id: String) {
            entities.remove(id)
        }

        override fun withSyncState(entity: TestTask, state: SyncState): TestTask =
            entity.copy(syncState = state)
    }
}