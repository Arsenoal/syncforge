package dev.syncforge.conflict

import dev.syncforge.entity.EntityRegistry
import dev.syncforge.entity.PullApplyOutcome
import dev.syncforge.entity.SyncedEntity
import dev.syncforge.entity.TypedEntitySyncHandler
import dev.syncforge.model.Change
import dev.syncforge.model.ChangeType
import dev.syncforge.model.SyncState
import dev.syncforge.network.RemoteDelta
import dev.syncforge.outbox.InMemoryOutboxRepository
import dev.syncforge.sync.OptimisticSyncCoordinator
import dev.syncforge.sync.OutboxReconciler
import dev.syncforge.sync.SyncConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
/**
 * §5 / job 1.2-11 — outbox stays aligned with conflict resolution outcomes.
 */
class OutboxReconcileTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun resolveConflict_acceptRemote_clearsStaleOutbox() {
        runBlocking {
            val fixture = reconcileFixture(deferPolicy())
            fixture.seedOutbox(localTitle = "local edit")
            fixture.seedDeferredConflict(localTitle = "local edit", remoteTitle = "remote edit")

            fixture.resolutionService.resolve(
                entityType = "tasks",
                entityId = "1",
                choice = ConflictChoice.AcceptRemote,
            )

            assertEquals(0, fixture.outbox.countAwaitingPush())
            assertEquals("remote edit", fixture.handler.require("1").title)
            assertEquals(SyncState.SYNCED, fixture.handler.require("1").syncState)
        }
    }

    @Test
    fun resolveConflict_keepLocal_retainsOutboxEntry() {
        runBlocking {
            val fixture = reconcileFixture(deferPolicy())
            val entry = fixture.seedOutbox(localTitle = "local edit")
            fixture.seedDeferredConflict(localTitle = "local edit", remoteTitle = "remote edit")

            fixture.resolutionService.resolve(
                entityType = "tasks",
                entityId = "1",
                choice = ConflictChoice.KeepLocal,
            )

            val pending = fixture.outbox.findForEntity("tasks", "1")
            assertEquals(1, pending.size)
            assertEquals(entry.id, pending.single().id)
            assertEquals(SyncState.PENDING, fixture.handler.require("1").syncState)
        }
    }

    @Test
    fun resolveConflict_acceptRemoteOnTombstone_deletesLocal() {
        runBlocking {
            val fixture = reconcileFixture(deferPolicy())
            fixture.seedOutbox(localTitle = "local edit")
            fixture.seedDeferredDeleteConflict(localTitle = "local edit")
            assertEquals(1, fixture.outbox.countAwaitingPush())

            fixture.resolutionService.resolve(
                entityType = "tasks",
                entityId = "1",
                choice = ConflictChoice.AcceptRemote,
            )

            assertEquals(0, fixture.outbox.countAwaitingPush())
            assertEquals(null, runCatching { fixture.handler.require("1") }.getOrNull())
            assertEquals(0, fixture.conflictStore.countOpen())
        }
    }

    @Test
    fun resolveConflict_customMerged_replacesOutboxWithUpdate() {
        runBlocking {
            val fixture = reconcileFixture(deferPolicy())
            fixture.seedOutbox(localTitle = "local edit")
            fixture.seedDeferredConflict(localTitle = "local edit", remoteTitle = "remote edit")
            val merged = Task("1", title = "merged title", updatedAtMillis = 500)

            fixture.resolutionService.resolve(
                entityType = "tasks",
                entityId = "1",
                choice = ConflictChoice.Custom(merged),
            )

            val pending = fixture.outbox.findForEntity("tasks", "1")
            assertEquals(1, pending.size)
            assertEquals(ChangeType.UPDATE, pending.single().changeType)
            assertEquals("merged title", json.decodeFromString<Task>(pending.single().payloadJson!!).title)
            assertEquals(SyncState.PENDING, fixture.handler.require("1").syncState)
        }
    }

    @Test
    fun pull_autoAcceptRemote_clearsStaleOutbox() {
        runBlocking {
            val fixture = reconcileFixture(alwaysRemotePolicy())
            fixture.seedOutbox(localTitle = "local edit")
            fixture.handler.put(
                Task("1", title = "local edit", updatedAtMillis = 100, syncState = SyncState.PENDING),
            )

            val outcome = fixture.pullApplier.applyDelta(
                handler = fixture.handler,
                delta = remoteDelta(Task("1", title = "remote edit", updatedAtMillis = 200)),
            )

            assertEquals(PullApplyOutcome.CONFLICT_RESOLVED, outcome)
            assertEquals(0, fixture.outbox.countAwaitingPush())
            assertEquals("remote edit", fixture.handler.require("1").title)
            assertEquals(SyncState.SYNCED, fixture.handler.require("1").syncState)
        }
    }

    @Test
    fun pull_autoMerged_enqueuesMergedUpdate() {
        runBlocking {
            val fixture = reconcileFixture(mergePolicy())
            fixture.seedOutbox(localTitle = "local", completed = true)
            fixture.handler.put(
                Task("1", title = "local", completed = true, updatedAtMillis = 100, syncState = SyncState.PENDING),
            )

            fixture.pullApplier.applyDelta(
                handler = fixture.handler,
                delta = remoteDelta(Task("1", title = "remote", completed = false, updatedAtMillis = 200)),
            )

            val pending = fixture.outbox.findForEntity("tasks", "1")
            assertEquals(1, pending.size)
            assertEquals(ChangeType.UPDATE, pending.single().changeType)
            val payload = json.decodeFromString<Task>(pending.single().payloadJson!!)
            assertEquals("remote", payload.title)
            assertEquals(true, payload.completed)
            assertEquals(4L, payload.localVersion)
            assertEquals(SyncState.PENDING, fixture.handler.require("1").syncState)
        }
    }

    private fun deferPolicy(): ConflictPolicy =
        conflictPolicy { entity("tasks") { deferToUser() } }

    private fun alwaysRemotePolicy(): ConflictPolicy =
        conflictPolicy { entity("tasks") { alwaysRemote() } }

    private fun mergePolicy(): ConflictPolicy =
        conflictPolicy {
            entity("tasks") {
                merge<Task> { local, remote ->
                    local.copy(
                        title = preferRemote(local.title, remote.title),
                        completed = preferLocal(local.completed, remote.completed),
                    )
                }
            }
        }

    private fun remoteDelta(remote: Task): RemoteDelta =
        RemoteDelta(
            entityType = "tasks",
            entityId = remote.id,
            payloadJson = json.encodeToString(remote),
            serverVersion = 3L,
            updatedAtMillis = remote.updatedAtMillis,
        )

    private fun reconcileFixture(policy: ConflictPolicy): ReconcileFixture {
        val outbox = InMemoryOutboxRepository()
        val conflictStore = InMemoryConflictStore()
        val handler = TaskHandler()
        val registry = EntityRegistry(listOf(handler))
        val coordinator = OptimisticSyncCoordinator(
            config = SyncConfig(entityTypes = setOf("tasks")),
            registry = registry,
            outbox = outbox,
        )
        val reconciler = OutboxReconciler(outbox, coordinator)
        val pullApplier = ConflictPullApplier(
            policy = policy,
            conflictStore = conflictStore,
            outboxReconciler = reconciler,
        )
        val resolutionService = ConflictResolutionService(
            registry = registry,
            conflictStore = conflictStore,
            conflictApplier = pullApplier,
        )
        return ReconcileFixture(
            outbox = outbox,
            conflictStore = conflictStore,
            handler = handler,
            pullApplier = pullApplier,
            resolutionService = resolutionService,
        )
    }

    private class ReconcileFixture(
        val outbox: InMemoryOutboxRepository,
        val conflictStore: InMemoryConflictStore,
        val handler: TaskHandler,
        val pullApplier: ConflictPullApplier,
        val resolutionService: ConflictResolutionService,
    ) {
        suspend fun seedOutbox(localTitle: String, completed: Boolean = false) =
            outbox.enqueue(
                change = Change.update(
                    "tasks",
                    Task("1", title = localTitle, completed = completed, updatedAtMillis = 100),
                ),
                payloadJson = handler.encode(Task("1", title = localTitle, completed = completed)),
                rollbackSnapshotJson = null,
            )

        suspend fun seedDeferredConflict(localTitle: String, remoteTitle: String) {
            conflictStore.recordDeferred(
                entityType = "tasks",
                entityId = "1",
                localJson = handler.encode(Task("1", title = localTitle)),
                remoteJson = handler.encode(Task("1", title = remoteTitle)),
                localUpdatedAtMillis = 100,
                remoteServerVersion = 3,
                remoteUpdatedAtMillis = 200,
                detectedAtMillis = 150,
            )
            handler.put(Task("1", title = localTitle, syncState = SyncState.CONFLICT))
        }

        suspend fun seedDeferredDeleteConflict(localTitle: String) {
            conflictStore.recordDeferred(
                entityType = "tasks",
                entityId = "1",
                localJson = handler.encode(Task("1", title = localTitle)),
                remoteJson = null,
                localUpdatedAtMillis = 100,
                remoteServerVersion = 3,
                remoteUpdatedAtMillis = 200,
                detectedAtMillis = 150,
            )
            handler.put(Task("1", title = localTitle, syncState = SyncState.CONFLICT))
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

    private class TaskHandler : TypedEntitySyncHandler<Task>() {

        override val entityType: String = "tasks"
        private val entities = mutableMapOf<String, Task>()
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

        fun require(id: String): Task = checkNotNull(entities[id])

        fun encode(task: Task): String = toJson(task)

        suspend fun put(task: Task) {
            insert(task)
        }
    }
}