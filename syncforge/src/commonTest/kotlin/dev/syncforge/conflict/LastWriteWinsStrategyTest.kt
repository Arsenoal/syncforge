package dev.syncforge.conflict

import dev.syncforge.entity.RemoteMetadata
import dev.syncforge.entity.SyncedEntity
import dev.syncforge.model.SyncState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LastWriteWinsStrategyTest {

    private data class Task(
        override val id: String,
        override val localVersion: Long,
        override val updatedAtMillis: Long,
        override val syncState: SyncState = SyncState.PENDING,
        val title: String = "",
    ) : SyncedEntity

    private val strategy = ConflictStrategies.lastWriteWins()

    @Test
    fun remoteNewer_acceptsRemote() {
        runBlocking {
            val local = Task("1", localVersion = 2, updatedAtMillis = 100L, title = "local")
            val remote = Task("1", localVersion = 3, updatedAtMillis = 200L, title = "remote")

            val outcome = strategy.resolve(
                ConflictContext(
                    entityType = "tasks",
                    local = local,
                    remote = RemoteMetadata(serverVersion = 3, updatedAtMillis = 200L),
                    remotePayload = remote,
                ),
            )

            assertIs<ConflictOutcome.Resolved<Task>>(outcome)
            val resolution = outcome.resolution
            assertIs<ConflictResolution.AcceptRemote<Task>>(resolution)
            assertEquals("remote", resolution.entity.title)
        }
    }

    @Test
    fun localNewer_keepsLocal() {
        runBlocking {
            val local = Task("1", localVersion = 2, updatedAtMillis = 300L)
            val remote = Task("1", localVersion = 3, updatedAtMillis = 200L)

            val outcome = strategy.resolve(
                ConflictContext(
                    entityType = "tasks",
                    local = local,
                    remote = RemoteMetadata(serverVersion = 3, updatedAtMillis = 200L),
                    remotePayload = remote,
                ),
            )

            assertIs<ConflictOutcome.Resolved<Task>>(outcome)
            assertIs<ConflictResolution.KeepLocal<Task>>(outcome.resolution)
        }
    }

    @Test
    fun remoteTombstone_deletesLocal() {
        runBlocking {
            val local = Task("1", localVersion = 1, updatedAtMillis = 100L)

            val outcome = strategy.resolve(
                ConflictContext(
                    entityType = "tasks",
                    local = local,
                    remote = RemoteMetadata(serverVersion = 2, updatedAtMillis = 200L, isDeleted = true),
                    remotePayload = null,
                ),
            )

            assertIs<ConflictOutcome.Resolved<Task>>(outcome)
            assertIs<ConflictResolution.DeleteLocal>(outcome.resolution)
        }
    }
}