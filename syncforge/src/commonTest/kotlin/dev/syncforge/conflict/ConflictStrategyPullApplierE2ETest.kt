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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * End-to-end pull conflict tests — every built-in [ConflictStrategy] through [ConflictPullApplier].
 *
 * Scenario: local row exists with pending edits; a newer remote delta arrives on pull.
 */
class ConflictStrategyPullApplierE2ETest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun lastWriteWins_remoteNewer_acceptsRemoteAndSyncs() {
        runBlocking {
            val handler = taskHandler(
                Task("1", title = "local", updatedAtMillis = 100, syncState = SyncState.PENDING),
            )
            val outcome = pullConflict(
                handler = handler,
                policy = policy { lastWriteWins() },
                remote = Task("1", title = "remote", updatedAtMillis = 200),
                serverVersion = 3L,
            )

            assertEquals(PullApplyOutcome.CONFLICT_RESOLVED, outcome)
            val persisted = handler.require("1")
            assertEquals("remote", persisted.title)
            assertEquals(SyncState.SYNCED, persisted.syncState)
        }
    }

    @Test
    fun lastWriteWins_localNewer_keepsLocalPending() {
        runBlocking {
            val handler = taskHandler(
                Task("1", title = "local", updatedAtMillis = 300, syncState = SyncState.PENDING),
            )
            val outcome = pullConflict(
                handler = handler,
                policy = policy { lastWriteWins() },
                remote = Task("1", title = "remote", updatedAtMillis = 200),
                serverVersion = 3L,
            )

            assertEquals(PullApplyOutcome.CONFLICT_RESOLVED, outcome)
            val persisted = handler.require("1")
            assertEquals("local", persisted.title)
            assertEquals(SyncState.PENDING, persisted.syncState)
        }
    }

    @Test
    fun lastWriteWins_remoteDelete_removesLocalRow() {
        runBlocking {
            val handler = taskHandler(
                Task("1", title = "local", syncState = SyncState.PENDING),
            )
            val applier = ConflictPullApplier(
                policy = policy { lastWriteWins() },
                conflictStore = NoOpConflictStore,
            )

            val outcome = applier.applyDelta(
                handler = handler,
                delta = RemoteDelta(
                    entityType = "tasks",
                    entityId = "1",
                    payloadJson = null,
                    serverVersion = 3L,
                    updatedAtMillis = 200L,
                    isDeleted = true,
                ),
            )

            assertEquals(PullApplyOutcome.DELETED, outcome)
            assertNull(handler.get("1"))
        }
    }

    @Test
    fun alwaysLocal_keepsLocalPending() {
        runBlocking {
            val handler = taskHandler(
                Task("1", title = "local", updatedAtMillis = 100, syncState = SyncState.PENDING),
            )
            val outcome = pullConflict(
                handler = handler,
                policy = policy { alwaysLocal() },
                remote = Task("1", title = "remote", updatedAtMillis = 200),
                serverVersion = 3L,
            )

            assertEquals(PullApplyOutcome.CONFLICT_RESOLVED, outcome)
            val persisted = handler.require("1")
            assertEquals("local", persisted.title)
            assertEquals(SyncState.PENDING, persisted.syncState)
        }
    }

    @Test
    fun alwaysRemote_acceptsRemoteAndSyncs() {
        runBlocking {
            val handler = taskHandler(
                Task("1", title = "local", updatedAtMillis = 300, syncState = SyncState.PENDING),
            )
            val outcome = pullConflict(
                handler = handler,
                policy = policy { alwaysRemote() },
                remote = Task("1", title = "remote", updatedAtMillis = 200),
                serverVersion = 3L,
            )

            assertEquals(PullApplyOutcome.CONFLICT_RESOLVED, outcome)
            val persisted = handler.require("1")
            assertEquals("remote", persisted.title)
            assertEquals(SyncState.SYNCED, persisted.syncState)
        }
    }

    @Test
    fun alwaysRemote_remoteDelete_removesLocalRow() {
        runBlocking {
            val handler = taskHandler(
                Task("1", title = "local", syncState = SyncState.PENDING),
            )
            val applier = ConflictPullApplier(
                policy = policy { alwaysRemote() },
                conflictStore = NoOpConflictStore,
            )

            val outcome = applier.applyDelta(
                handler = handler,
                delta = RemoteDelta(
                    entityType = "tasks",
                    entityId = "1",
                    payloadJson = null,
                    serverVersion = 3L,
                    updatedAtMillis = 200L,
                    isDeleted = true,
                ),
            )

            assertEquals(PullApplyOutcome.DELETED, outcome)
            assertNull(handler.get("1"))
        }
    }

    @Test
    fun deferToUser_recordsOpenConflictAndMarksEntityConflict() {
        runBlocking {
            val conflictStore = InMemoryConflictStore()
            val handler = taskHandler(
                Task("1", title = "local", updatedAtMillis = 300, syncState = SyncState.PENDING),
            )
            val applier = ConflictPullApplier(
                policy = policy { deferToUser() },
                conflictStore = conflictStore,
            )

            val outcome = applier.applyDelta(
                handler = handler,
                delta = remoteDelta(Task("1", title = "remote", updatedAtMillis = 200)),
            )

            assertEquals(PullApplyOutcome.CONFLICT_RESOLVED, outcome)
            val persisted = handler.require("1")
            assertEquals("local", persisted.title)
            assertEquals(SyncState.CONFLICT, persisted.syncState)
            assertEquals(1, conflictStore.countOpen())
            val record = conflictStore.findOpen("tasks", "1")
            assertNotNull(record)
            assertEquals("local", json.decodeFromString<Task>(record.localJson).title)
            assertEquals("remote", record.remoteJson?.let { json.decodeFromString<Task>(it).title })
        }
    }

    @Test
    fun merge_combinesFieldsAndSyncs() {
        runBlocking {
            val handler = taskHandler(
                Task(
                    id = "1",
                    title = "local",
                    completed = true,
                    updatedAtMillis = 100,
                    syncState = SyncState.PENDING,
                ),
            )
            val outcome = pullConflict(
                handler = handler,
                policy = conflictPolicy {
                    entity("tasks") {
                        merge<Task> { local, remote ->
                            local.copy(
                                title = preferRemote(local.title, remote.title),
                                completed = preferLocal(local.completed, remote.completed),
                            )
                        }
                    }
                },
                remote = Task("1", title = "remote", completed = false, updatedAtMillis = 200),
                serverVersion = 3L,
            )

            assertEquals(PullApplyOutcome.CONFLICT_RESOLVED, outcome)
            val persisted = handler.require("1")
            assertEquals("remote", persisted.title)
            assertEquals(true, persisted.completed)
            assertEquals(SyncState.SYNCED, persisted.syncState)
        }
    }

    @Test
    fun gitLike_autoMergesNonOverlappingFieldsAndSyncs() {
        runBlocking {
            val store = InMemoryMergeBaseStore()
            store.put(
                MergeBaseSnapshot(
                    entityType = "tasks",
                    entityId = "1",
                    payloadJson = json.encodeToString(Task("1", title = "shared", completed = false)),
                    serverVersion = 1L,
                    updatedAtMillis = 50L,
                    storedAtMillis = 50L,
                ),
            )
            val handler = taskHandler(
                Task("1", title = "local title", completed = false, syncState = SyncState.PENDING),
            )
            val applier = ConflictPullApplier(
                policy = gitLikePolicy(),
                conflictStore = NoOpConflictStore,
                mergeBaseRecorder = MergeBaseRecorder(store),
            )

            val outcome = applier.applyDelta(
                handler = handler,
                delta = remoteDelta(Task("1", title = "shared", completed = true, updatedAtMillis = 200)),
            )

            assertEquals(PullApplyOutcome.CONFLICT_RESOLVED, outcome)
            val persisted = handler.require("1")
            assertEquals("local title", persisted.title)
            assertEquals(true, persisted.completed)
            assertEquals(SyncState.SYNCED, persisted.syncState)
        }
    }

    @Test
    fun gitLike_unmergeableDefersToUserAndRecordsConflict() {
        runBlocking {
            val store = InMemoryMergeBaseStore()
            store.put(
                MergeBaseSnapshot(
                    entityType = "tasks",
                    entityId = "1",
                    payloadJson = json.encodeToString(Task("1", title = "base", completed = false)),
                    serverVersion = 1L,
                    updatedAtMillis = 50L,
                    storedAtMillis = 50L,
                ),
            )
            val conflictStore = InMemoryConflictStore()
            val handler = taskHandler(
                Task("1", title = "local edit", completed = false, syncState = SyncState.PENDING),
            )
            val applier = ConflictPullApplier(
                policy = gitLikePolicy(),
                conflictStore = conflictStore,
                mergeBaseRecorder = MergeBaseRecorder(store),
            )

            val outcome = applier.applyDelta(
                handler = handler,
                delta = remoteDelta(Task("1", title = "remote edit", completed = false, updatedAtMillis = 200)),
            )

            assertEquals(PullApplyOutcome.CONFLICT_RESOLVED, outcome)
            val persisted = handler.require("1")
            assertEquals("local edit", persisted.title)
            assertEquals(SyncState.CONFLICT, persisted.syncState)
            assertEquals(1, conflictStore.countOpen())
        }
    }

    @Test
    fun crdt_unionsConcurrentTagAddsAndSyncs() {
        runBlocking {
            val handler = documentHandler(
                TaggedDocument(
                    id = "1",
                    tags = listOf("alpha"),
                    updatedAtMillis = 100,
                    syncState = SyncState.PENDING,
                ),
            )
            val applier = ConflictPullApplier(
                policy = crdtPolicy(),
                conflictStore = NoOpConflictStore,
            )

            val outcome = applier.applyDelta(
                handler = handler,
                delta = RemoteDelta(
                    entityType = "documents",
                    entityId = "1",
                    payloadJson = json.encodeToString(
                        TaggedDocument("1", tags = listOf("beta"), updatedAtMillis = 200),
                    ),
                    serverVersion = 3L,
                    updatedAtMillis = 200L,
                ),
            )

            assertEquals(PullApplyOutcome.CONFLICT_RESOLVED, outcome)
            val persisted = handler.require("1")
            assertEquals(setOf("alpha", "beta"), persisted.tags.toSet())
            assertEquals(SyncState.SYNCED, persisted.syncState)
        }
    }

    private fun policy(block: ConflictEntityBuilder.() -> Unit): ConflictPolicy =
        conflictPolicy { entity("tasks", block) }

    private suspend fun pullConflict(
        handler: TaskHandler,
        policy: ConflictPolicy,
        remote: Task,
        serverVersion: Long,
    ): PullApplyOutcome {
        val applier = ConflictPullApplier(
            policy = policy,
            conflictStore = NoOpConflictStore,
        )
        return applier.applyDelta(
            handler = handler,
            delta = remoteDelta(remote, serverVersion),
        )
    }

    private fun remoteDelta(remote: Task, serverVersion: Long = 3L): RemoteDelta =
        RemoteDelta(
            entityType = "tasks",
            entityId = remote.id,
            payloadJson = json.encodeToString(remote),
            serverVersion = serverVersion,
            updatedAtMillis = remote.updatedAtMillis,
        )

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

    private fun crdtPolicy(): ConflictPolicy =
        conflictPolicy {
            entity("documents") {
                crdt<TaggedDocument> {
                    field("tags") { orSet() }
                }
            }
        }

    @Serializable
    private data class Task(
        override val id: String,
        val title: String = "task",
        val completed: Boolean = false,
        override val localVersion: Long = 1,
        override val updatedAtMillis: Long = 100L,
        override val syncState: SyncState = SyncState.SYNCED,
    ) : SyncedEntity

    @Serializable
    private data class TaggedDocument(
        override val id: String,
        val tags: List<String> = emptyList(),
        override val localVersion: Long = 1,
        override val updatedAtMillis: Long = 100L,
        override val syncState: SyncState = SyncState.SYNCED,
    ) : SyncedEntity

    private fun taskHandler(initial: Task): TaskHandler = TaskHandler(initial)

    private fun documentHandler(initial: TaggedDocument): DocumentHandler = DocumentHandler(initial)

    private open class TaskHandler(
        initial: Task,
    ) : TypedEntitySyncHandler<Task>() {

        override val entityType: String = "tasks"
        private val entities = mutableMapOf(initial.id to initial)
        private val codec = Json { ignoreUnknownKeys = true }

        override fun toJson(entity: Task): String = codec.encodeToString(entity)

        override fun fromJson(json: String): Task = codec.decodeFromString(json)

        override suspend fun findById(id: String): Task? = entities[id]

        override suspend fun insert(entity: Task) {
            entities[entity.id] = entity
        }

        override suspend fun update(entity: Task) {
            entities[entity.id] = entity
        }

        override suspend fun deleteById(id: String) {
            entities.remove(id)
        }

        override fun withSyncState(entity: Task, state: SyncState): Task = entity.copy(syncState = state)

        fun get(id: String): Task? = entities[id]

        fun require(id: String): Task = checkNotNull(entities[id])
    }

    private class DocumentHandler(
        initial: TaggedDocument,
    ) : TypedEntitySyncHandler<TaggedDocument>() {

        override val entityType: String = "documents"
        private val entities = mutableMapOf(initial.id to initial)
        private val codec = Json { ignoreUnknownKeys = true }

        override fun toJson(entity: TaggedDocument): String = codec.encodeToString(entity)

        override fun fromJson(json: String): TaggedDocument = codec.decodeFromString(json)

        override suspend fun findById(id: String): TaggedDocument? = entities[id]

        override suspend fun insert(entity: TaggedDocument) {
            entities[entity.id] = entity
        }

        override suspend fun update(entity: TaggedDocument) {
            entities[entity.id] = entity
        }

        override suspend fun deleteById(id: String) {
            entities.remove(id)
        }

        override fun withSyncState(entity: TaggedDocument, state: SyncState): TaggedDocument =
            entity.copy(syncState = state)

        fun require(id: String): TaggedDocument = checkNotNull(entities[id])
    }
}