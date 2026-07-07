package dev.syncforge.sync

import dev.syncforge.conflict.InMemoryMergeBaseStore
import dev.syncforge.conflict.MergeBaseRecorder
import dev.syncforge.entity.EntityRegistry
import dev.syncforge.entity.PullApplyOutcome
import dev.syncforge.entity.SyncedEntity
import dev.syncforge.entity.TypedEntitySyncHandler
import dev.syncforge.model.Change
import dev.syncforge.model.OutboxEntry
import dev.syncforge.model.SyncState
import dev.syncforge.network.PullResult
import dev.syncforge.network.PushResult
import dev.syncforge.network.SyncTransport
import dev.syncforge.outbox.InMemoryOutboxRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MergeBasePushAckTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun runPush_recordsMergeBaseAfterAcknowledgement() = runTest {
        val store = InMemoryMergeBaseStore()
        val handler = TestTaskHandler(
            TestTask("t1", title = "pushed", syncState = SyncState.PENDING),
        )
        val outbox = InMemoryOutboxRepository()
        val pending = TestTask("t1", title = "pushed", syncState = SyncState.PENDING)
        val entry = outbox.enqueue(
            change = Change.update("tasks", pending),
            payloadJson = json.encodeToString(pending),
            rollbackSnapshotJson = null,
        )
        val engine = SyncEngine(
            config = SyncConfig(entityTypes = setOf("tasks")),
            outbox = outbox,
            transport = AckAllTransport(entry.id),
            registry = EntityRegistry(listOf(handler)),
            mergeBaseRecorder = MergeBaseRecorder(store),
        )

        engine.runPush()

        val base = store.get("tasks", "t1")
        assertNotNull(base)
        assertEquals("pushed", json.decodeFromString<TestTask>(base.payloadJson).title)
    }

    @Test
    fun runPush_removesMergeBaseOnDeleteAcknowledgement() = runTest {
        val store = InMemoryMergeBaseStore()
        store.put(
            dev.syncforge.conflict.MergeBaseSnapshot(
                entityType = "tasks",
                entityId = "t1",
                payloadJson = "{}",
                serverVersion = 1L,
                updatedAtMillis = 1L,
                storedAtMillis = 1L,
            ),
        )
        val handler = TestTaskHandler(TestTask("t1"))
        val outbox = InMemoryOutboxRepository()
        val entry = outbox.enqueue(
            change = Change.delete<TestTask>("tasks", "t1", localVersion = 1, updatedAtMillis = 100L),
            payloadJson = null,
            rollbackSnapshotJson = json.encodeToString(TestTask("t1")),
        )
        val engine = SyncEngine(
            config = SyncConfig(entityTypes = setOf("tasks")),
            outbox = outbox,
            transport = AckAllTransport(entry.id),
            registry = EntityRegistry(listOf(handler)),
            mergeBaseRecorder = MergeBaseRecorder(store),
        )

        engine.runPush()

        assertNull(store.get("tasks", "t1"))
    }

    @Serializable
    private data class TestTask(
        override val id: String,
        val title: String = "task",
        override val localVersion: Long = 1,
        override val updatedAtMillis: Long = 100L,
        override val syncState: SyncState = SyncState.SYNCED,
    ) : SyncedEntity

    private class TestTaskHandler(
        initial: TestTask,
    ) : TypedEntitySyncHandler<TestTask>() {

        override val entityType: String = "tasks"
        private var entity: TestTask = initial
        private val json = Json { ignoreUnknownKeys = true }

        override fun toJson(entity: TestTask): String = json.encodeToString(entity)

        override fun fromJson(json: String): TestTask = this.json.decodeFromString(json)

        override suspend fun findById(id: String): TestTask? = entity.takeIf { it.id == id }

        override suspend fun insert(entity: TestTask) {
            this.entity = entity
        }

        override suspend fun update(entity: TestTask) {
            this.entity = entity
        }

        override suspend fun deleteById(id: String) {
            if (entity.id == id) {
                entity = entity.copy(syncState = SyncState.SYNCED)
            }
        }

        override suspend fun onPushAcknowledged(entryEntityId: String) {
            val current = findById(entryEntityId) ?: return
            update(withSyncState(current, SyncState.SYNCED))
        }

        override suspend fun applyPullDelta(delta: dev.syncforge.network.RemoteDelta): PullApplyOutcome =
            PullApplyOutcome.SKIPPED

        override fun withSyncState(entity: TestTask, state: SyncState): TestTask =
            entity.copy(syncState = state)
    }

    private class AckAllTransport(
        private val ackId: Long,
    ) : SyncTransport {
        override suspend fun push(entries: List<OutboxEntry>): PushResult =
            PushResult(acknowledgedIds = listOf(ackId))

        override suspend fun pull(
            sinceTimestampMillis: Long,
            entityTypes: Set<String>,
            pageSize: Int,
            pageCursor: String?,
        ): PullResult = PullResult(deltas = emptyList(), serverTimestampMillis = sinceTimestampMillis)
    }
}