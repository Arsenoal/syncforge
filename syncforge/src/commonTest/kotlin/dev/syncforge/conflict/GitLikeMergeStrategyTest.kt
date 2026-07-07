@file:OptIn(dev.syncforge.api.ExperimentalSyncForgeApi::class)

package dev.syncforge.conflict

import dev.syncforge.entity.PullApplyOutcome
import dev.syncforge.entity.RemoteMetadata
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
import kotlin.test.assertIs

class GitLikeMergeStrategyTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun threeWayMerge_autoMergesNonOverlappingFields() {
        runBlocking {
            val strategy = gitLikePolicy().strategyFor("tasks")
            val base = Task("1", "shared", completed = false)
            val local = Task("1", "local title", completed = false)
            val remote = Task("1", "shared", completed = true)

            val outcome = strategy.resolve(
                ConflictContext(
                    entityType = "tasks",
                    local = local,
                    remote = RemoteMetadata(serverVersion = 3, updatedAtMillis = 200),
                    remotePayload = remote,
                    mergeBasePayload = base,
                ),
            )

            assertIs<ConflictOutcome.Resolved<Task>>(outcome)
            val merged = (outcome as ConflictOutcome.Resolved).resolution
            assertIs<ConflictResolution.Merged<Task>>(merged)
            assertEquals("local title", merged.entity.title)
            assertEquals(true, merged.entity.completed)
        }
    }

    @Test
    fun threeWayMerge_unmergeableDefersToUser() {
        runBlocking {
            val strategy = gitLikePolicy().strategyFor("tasks")
            val base = Task("1", "base", completed = false)
            val local = Task("1", "local edit", completed = false)
            val remote = Task("1", "remote edit", completed = false)

            val outcome = strategy.resolve(
                ConflictContext(
                    entityType = "tasks",
                    local = local,
                    remote = RemoteMetadata(serverVersion = 3, updatedAtMillis = 200),
                    remotePayload = remote,
                    mergeBasePayload = base,
                ),
            )

            assertIs<ConflictOutcome.Deferred<Task>>(outcome)
            assertEquals("local edit", outcome.local.title)
            assertEquals("remote edit", outcome.remote?.title)
        }
    }

    @Test
    fun threeWayMerge_missingBaseUsesLocalAsSyntheticBase() {
        runBlocking {
            val strategy = gitLikePolicy().strategyFor("tasks")
            val local = Task("1", "local", completed = false)
            val remote = Task("1", "local", completed = true)

            val outcome = strategy.resolve(
                ConflictContext(
                    entityType = "tasks",
                    local = local,
                    remote = RemoteMetadata(serverVersion = 2, updatedAtMillis = 200),
                    remotePayload = remote,
                    mergeBasePayload = null,
                ),
            )

            assertIs<ConflictOutcome.Resolved<Task>>(outcome)
            val merged = (outcome as ConflictOutcome.Resolved).resolution
            assertIs<ConflictResolution.Merged<Task>>(merged)
            assertEquals("local", merged.entity.title)
            assertEquals(true, merged.entity.completed)
        }
    }

    @Test
    fun pullApplier_loadsMergeBaseAndAutoMerges() {
        runBlocking {
            val store = InMemoryMergeBaseStore()
            val recorder = MergeBaseRecorder(store)
            val handler = TestTaskHandler(
                initial = Task("1", "local title", completed = false, syncState = SyncState.PENDING),
            )
            store.put(
                MergeBaseSnapshot(
                    entityType = "tasks",
                    entityId = "1",
                    payloadJson = json.encodeToString(Task("1", "shared", completed = false)),
                    serverVersion = 1L,
                    updatedAtMillis = 50L,
                    storedAtMillis = 50L,
                ),
            )

            val applier = ConflictPullApplier(
                policy = gitLikePolicy(),
                conflictStore = NoOpConflictStore,
                mergeBaseRecorder = recorder,
            )

            val outcome = applier.applyDelta(
                handler = handler,
                delta = RemoteDelta(
                    entityType = "tasks",
                    entityId = "1",
                    payloadJson = json.encodeToString(Task("1", "shared", completed = true)),
                    serverVersion = 3L,
                    updatedAtMillis = 200L,
                ),
            )

            assertEquals(PullApplyOutcome.CONFLICT_RESOLVED, outcome)
            val persisted = handler.current()
            assertEquals("local title", persisted?.title)
            assertEquals(true, persisted?.completed)
            assertEquals(SyncState.SYNCED, persisted?.syncState)
        }
    }

    @Test
    fun kindOf_recognizesGitLikeStrategy() {
        val policy = gitLikePolicy()
        assertEquals(ConflictStrategyKind.GIT_LIKE, ConflictStrategies.kindOf(policy.strategyFor("tasks")))
    }

    private fun gitLikePolicy(): ConflictPolicy =
        conflictPolicy {
            entity("tasks") {
                gitLike<Task> {
                    threeWayMerge { base, local, remote ->
                        val titleConflict =
                            local.title != base.title && remote.title != base.title && local.title != remote.title
                        val completedConflict =
                            local.completed != base.completed &&
                                remote.completed != base.completed &&
                                local.completed != remote.completed
                        if (titleConflict || completedConflict) {
                            ThreeWayMergeResult.Unmergeable
                        } else {
                            ThreeWayMergeResult.Merged(
                                local.copy(
                                    title = when {
                                        local.title != base.title -> local.title
                                        remote.title != base.title -> remote.title
                                        else -> local.title
                                    },
                                    completed = when {
                                        local.completed != base.completed -> local.completed
                                        remote.completed != base.completed -> remote.completed
                                        else -> local.completed
                                    },
                                ),
                            )
                        }
                    }
                    onUnmergeable { deferToUser() }
                }
            }
        }

    @Serializable
    private data class Task(
        override val id: String,
        val title: String,
        val completed: Boolean = false,
        override val localVersion: Long = 1,
        override val updatedAtMillis: Long = 100L,
        override val syncState: SyncState = SyncState.SYNCED,
    ) : SyncedEntity

    private class TestTaskHandler(
        initial: Task,
    ) : TypedEntitySyncHandler<Task>() {

        override val entityType: String = "tasks"
        private var entity: Task = initial
        private val json = Json { ignoreUnknownKeys = true }

        override fun toJson(entity: Task): String = json.encodeToString(entity)

        override fun fromJson(json: String): Task = this.json.decodeFromString(json)

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

        fun current(): Task = entity
    }
}