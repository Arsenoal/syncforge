package dev.syncforge.conflict

import dev.syncforge.entity.RemoteMetadata
import dev.syncforge.entity.SyncedEntity
import dev.syncforge.model.SyncState
import kotlin.test.Test
import kotlin.test.assertIs

@Suppress("DEPRECATION")
class LastWriteWinsResolverTest {

    private data class Task(
        override val id: String,
        override val localVersion: Long,
        override val updatedAtMillis: Long,
        override val syncState: SyncState = SyncState.PENDING,
        val title: String = "",
    ) : SyncedEntity

    private val resolver = LastWriteWinsResolver()

    @Test
    fun remoteNewer_acceptsRemote() {
        val local = Task("1", localVersion = 2, updatedAtMillis = 100L, title = "local")
        val remote = Task("1", localVersion = 3, updatedAtMillis = 200L, title = "remote")

        val result = resolver.resolve(
            local = local,
            remote = RemoteMetadata(serverVersion = 3, updatedAtMillis = 200L),
            remotePayload = remote,
        )

        assertIs<ConflictResolution.AcceptRemote<Task>>(result)
        assertEquals("remote", result.entity.title)
    }

    @Test
    fun localNewer_keepsLocal() {
        val local = Task("1", localVersion = 2, updatedAtMillis = 300L)
        val remote = Task("1", localVersion = 3, updatedAtMillis = 200L)

        val result = resolver.resolve(
            local = local,
            remote = RemoteMetadata(serverVersion = 3, updatedAtMillis = 200L),
            remotePayload = remote,
        )

        assertIs<ConflictResolution.KeepLocal<Task>>(result)
    }

    @Test
    fun remoteTombstone_deletesLocal() {
        val local = Task("1", localVersion = 1, updatedAtMillis = 100L)

        val result = resolver.resolve(
            local = local,
            remote = RemoteMetadata(serverVersion = 2, updatedAtMillis = 200L, isDeleted = true),
            remotePayload = null,
        )

        assertIs<ConflictResolution.DeleteLocal>(result)
    }

    private fun assertEquals(expected: String, actual: String) {
        kotlin.test.assertEquals(expected, actual)
    }
}