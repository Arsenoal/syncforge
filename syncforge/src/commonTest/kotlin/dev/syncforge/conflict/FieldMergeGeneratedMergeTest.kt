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

/**
 * Exercises merge semantics produced by KSP [TaskEntityFieldMerge]-style codegen (§7).
 */
class FieldMergeGeneratedMergeTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun kspStyleMerge_unionsTagsAndPrefersNewerTitleOnPull() {
        runBlocking {
            val handler = TaskHandler(
                Task(
                    id = "1",
                    title = "local title",
                    tags = listOf("alpha"),
                    views = 4,
                    updatedAtMillis = 300,
                    syncState = SyncState.PENDING,
                ),
            )
            val applier = ConflictPullApplier(
                policy = conflictPolicy {
                    entity("tasks") {
                        merge<Task> { local, remote ->
                            TaskFieldMerge.merge(local, remote, remoteMeta.updatedAtMillis)
                        }
                    }
                },
                conflictStore = NoOpConflictStore,
            )

            val outcome = applier.applyDelta(
                handler = handler,
                delta = RemoteDelta(
                    entityType = "tasks",
                    entityId = "1",
                    payloadJson = json.encodeToString(
                        Task("1", title = "remote title", tags = listOf("beta"), views = 7, updatedAtMillis = 200),
                    ),
                    serverVersion = 3L,
                    updatedAtMillis = 200L,
                ),
            )

            assertEquals(PullApplyOutcome.CONFLICT_RESOLVED, outcome)
            val merged = handler.require("1")
            assertEquals("local title", merged.title)
            assertEquals(setOf("alpha", "beta"), merged.tags.toSet())
            assertEquals(7, merged.views)
            assertEquals(SyncState.SYNCED, merged.syncState)
        }
    }

    /** Mirrors KSP-generated TaskEntityFieldMerge for annotated fields. */
    private object TaskFieldMerge {
        fun merge(local: Task, remote: Task, remoteUpdatedAtMillis: Long): Task =
            local.copy(
                title = if (remoteUpdatedAtMillis >= local.updatedAtMillis) remote.title else local.title,
                tags = (local.tags + remote.tags).distinct(),
                views = maxOf(local.views, remote.views),
                updatedAtMillis = maxOf(local.updatedAtMillis, remote.updatedAtMillis),
                localVersion = maxOf(local.localVersion, remote.localVersion),
            )
    }

    @Serializable
    private data class Task(
        override val id: String,
        val title: String,
        val tags: List<String> = emptyList(),
        val views: Int = 0,
        override val localVersion: Long = 1,
        override val updatedAtMillis: Long = 100L,
        override val syncState: SyncState = SyncState.SYNCED,
    ) : SyncedEntity

    private class TaskHandler(initial: Task) : TypedEntitySyncHandler<Task>() {

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