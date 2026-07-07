package dev.syncforge.conflict

import dev.syncforge.entity.PullApplyOutcome
import dev.syncforge.entity.SyncedEntity
import dev.syncforge.entity.TypedEntitySyncHandler
import dev.syncforge.model.SyncState
import dev.syncforge.network.RemoteDelta
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
class UpdateConflictPolicyTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun conflictPolicyFromKinds_buildsCatalogPolicy() {
        val policy = conflictPolicyFromKinds(
            perEntity = mapOf(
                "notes" to ConflictStrategyKind.ACCEPT_REMOTE,
                "tasks" to ConflictStrategyKind.DEFER_TO_USER,
            ),
            defaultKind = ConflictStrategyKind.ACCEPT_LOCAL,
        )

        assertEquals(ConflictStrategyKind.ACCEPT_REMOTE, ConflictStrategies.kindOf(policy.strategyFor("notes")))
        assertEquals(ConflictStrategyKind.DEFER_TO_USER, ConflictStrategies.kindOf(policy.strategyFor("tasks")))
        assertEquals(ConflictStrategyKind.ACCEPT_LOCAL, ConflictStrategies.kindOf(policy.strategyFor("settings")))
    }

    @Test
    fun updatePolicy_nextPullUsesNewStrategy() {
        runBlocking {
            val holder = MutableConflictPolicy(
                conflictPolicy { entity("tasks") { alwaysRemote() } },
            )
            val handler = TaskHandler(
                Task("1", title = "local", updatedAtMillis = 300, syncState = SyncState.PENDING),
            )
            val applier = ConflictPullApplier(
                policy = holder,
                conflictStore = NoOpConflictStore,
            )

            holder.update(
                conflictPolicy { entity("tasks") { alwaysLocal() } },
            )

            val outcome = applier.applyDelta(
                handler = handler,
                delta = RemoteDelta(
                    entityType = "tasks",
                    entityId = "1",
                    payloadJson = json.encodeToString(Task("1", title = "remote", updatedAtMillis = 200)),
                    serverVersion = 3L,
                    updatedAtMillis = 200L,
                ),
            )

            assertEquals(PullApplyOutcome.CONFLICT_RESOLVED, outcome)
            assertEquals("local", handler.require("1").title)
            assertEquals(SyncState.PENDING, handler.require("1").syncState)
        }
    }

    @Test
    fun updatePolicy_doesNotChangeOpenDeferredConflicts() {
        runBlocking {
            val conflictStore = InMemoryConflictStore()
            val holder = MutableConflictPolicy(
                conflictPolicy { entity("tasks") { deferToUser() } },
            )
            val handler = TaskHandler(
                Task("1", title = "local", syncState = SyncState.CONFLICT),
            )
            conflictStore.recordDeferred(
                entityType = "tasks",
                entityId = "1",
                localJson = json.encodeToString(Task("1", title = "local")),
                remoteJson = json.encodeToString(Task("1", title = "remote")),
                localUpdatedAtMillis = 100,
                remoteServerVersion = 3,
                remoteUpdatedAtMillis = 200,
                detectedAtMillis = 150,
            )

            holder.update(
                conflictPolicy { entity("tasks") { alwaysRemote() } },
            )

            assertEquals(1, conflictStore.countOpen())
            val record = conflictStore.findOpen("tasks", "1")
            assertEquals("local", json.decodeFromString<Task>(record!!.localJson).title)
            assertEquals(SyncState.CONFLICT, handler.require("1").syncState)
        }
    }

    @Test
    fun updatePolicy_viaMutableHolder_changesKindOf() {
        val holder = MutableConflictPolicy(ConflictPolicy.Default)
        assertEquals(
            ConflictStrategyKind.LAST_WRITE_WINS,
            ConflictStrategies.kindOf(holder.snapshot().strategyFor("tasks")),
        )

        holder.update(
            conflictPolicyFromKinds(mapOf("tasks" to ConflictStrategyKind.DEFER_TO_USER)),
        )

        assertEquals(
            ConflictStrategyKind.DEFER_TO_USER,
            ConflictStrategies.kindOf(holder.snapshot().strategyFor("tasks")),
        )
    }

    @Serializable
    private data class Task(
        override val id: String,
        val title: String = "task",
        override val localVersion: Long = 1,
        override val updatedAtMillis: Long = 100L,
        override val syncState: SyncState = SyncState.SYNCED,
    ) : SyncedEntity

    private class TaskHandler(
        initial: Task,
    ) : TypedEntitySyncHandler<Task>() {

        override val entityType: String = "tasks"
        private var entity: Task = initial
        private val codec = Json { ignoreUnknownKeys = true }

        override fun toJson(entity: Task): String = codec.encodeToString(entity)

        override fun fromJson(json: String): Task = codec.decodeFromString(json)

        override suspend fun findById(id: String): Task? = entity.takeIf { it.id == id }

        override suspend fun insert(entity: Task) {
            this.entity = entity
        }

        override suspend fun update(entity: Task) {
            this.entity = entity
        }

        override suspend fun deleteById(id: String) {
            if (entity.id == id) entity = entity.copy(syncState = SyncState.SYNCED)
        }

        override fun withSyncState(entity: Task, state: SyncState): Task = entity.copy(syncState = state)

        fun require(id: String): Task = entity
    }
}