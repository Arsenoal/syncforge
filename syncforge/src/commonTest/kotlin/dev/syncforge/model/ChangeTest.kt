package dev.syncforge.model

import dev.syncforge.entity.SyncedEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChangeTest {

    private data class TestNote(
        override val id: String,
        override val localVersion: Long,
        override val updatedAtMillis: Long,
        override val syncState: SyncState = SyncState.PENDING,
        val body: String = "",
    ) : SyncedEntity

    @Test
    fun create_buildsCreateChange() {
        val note = TestNote("n1", localVersion = 1, updatedAtMillis = 100L)
        val change = Change.create<TestNote>("notes", note)

        assertEquals(ChangeType.CREATE, change.type)
        assertEquals("n1", change.entityId)
        assertEquals(note, change.payload)
    }

    @Test
    fun delete_rejectsNonNullPayload() {
        assertFailsWith<IllegalArgumentException> {
            Change(
                entityType = "notes",
                entityId = "n1",
                type = ChangeType.DELETE,
                payload = TestNote("n1", localVersion = 1, updatedAtMillis = 100L),
                localVersion = 1,
                updatedAtMillis = 100L,
            )
        }
    }
}