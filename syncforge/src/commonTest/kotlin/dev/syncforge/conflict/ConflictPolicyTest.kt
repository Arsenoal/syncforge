package dev.syncforge.conflict

import dev.syncforge.entity.RemoteMetadata
import dev.syncforge.entity.SyncedEntity
import dev.syncforge.model.SyncState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ConflictPolicyTest {

    private data class Task(
        override val id: String,
        val title: String,
        override val localVersion: Long,
        override val updatedAtMillis: Long,
        override val syncState: SyncState = SyncState.PENDING,
        val completed: Boolean = false,
    ) : SyncedEntity

    @Test
    fun defaultStrategy_acceptsNewerRemote() = runBlocking {
        val policy = ConflictPolicy.Default
        val local = Task("1", "local", localVersion = 2, updatedAtMillis = 100)
        val remote = Task("1", "remote", localVersion = 3, updatedAtMillis = 200)

        val outcome = policy.strategyFor("tasks").resolve(
            ConflictContext(
                entityType = "tasks",
                local = local,
                remote = RemoteMetadata(serverVersion = 3, updatedAtMillis = 200),
                remotePayload = remote,
            ),
        )

        assertIs<ConflictOutcome.Resolved<Task>>(outcome)
        val resolution = outcome.resolution
        assertIs<ConflictResolution.AcceptRemote<Task>>(resolution)
        assertEquals("remote", resolution.entity.title)
    }

    @Test
    fun entityOverride_deferToUser() = runBlocking {
        val policy = conflictPolicy {
            entity("tasks") { deferToUser() }
        }
        val local = Task("1", "local", localVersion = 2, updatedAtMillis = 300)
        val remote = Task("1", "remote", localVersion = 3, updatedAtMillis = 200)

        val outcome = policy.strategyFor("tasks").resolve(
            ConflictContext(
                entityType = "tasks",
                local = local,
                remote = RemoteMetadata(serverVersion = 3, updatedAtMillis = 200),
                remotePayload = remote,
            ),
        )

        assertIs<ConflictOutcome.Deferred<Task>>(outcome)
        assertEquals("local", outcome.local.title)
    }

    @Test
    fun mergeStrategy_mergesFields() {
        val policy = conflictPolicy {
            entity("tasks") {
                merge<Task> { local, remote ->
                    local.copy(
                        title = preferRemote(local.title, remote.title),
                        completed = preferLocal(local.completed, remote.completed),
                    )
                }
            }
        }

        val strategy = policy.strategyFor("tasks")
        val local = Task("1", "local", localVersion = 2, updatedAtMillis = 100, completed = true)
        val remote = Task("1", "remote", localVersion = 3, updatedAtMillis = 200, completed = false)

        val outcome = runBlocking {
            strategy.resolve(
                ConflictContext(
                    entityType = "tasks",
                    local = local,
                    remote = RemoteMetadata(serverVersion = 3, updatedAtMillis = 200),
                    remotePayload = remote,
                ),
            )
        }

        assertIs<ConflictOutcome.Resolved<Task>>(outcome)
        val merged = (outcome as ConflictOutcome.Resolved).resolution
        assertIs<ConflictResolution.Merged<Task>>(merged)
        assertEquals("remote", merged.entity.title)
        assertEquals(true, merged.entity.completed)
    }

    @Test
    fun mergeStrategy_kindOfReturnsMerge() {
        val policy = conflictPolicy {
            entity("tasks") {
                merge<Task> { local, remote ->
                    local.copy(title = preferRemote(local.title, remote.title))
                }
            }
        }
        assertEquals(ConflictStrategyKind.MERGE, ConflictStrategies.kindOf(policy.strategyFor("tasks")))
    }
}