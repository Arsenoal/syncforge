package dev.syncforge.sample.notes

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.syncforge.annotations.SyncForgeEntity
import dev.syncforge.entity.SyncedEntity
import dev.syncforge.model.SyncState
import kotlinx.serialization.Serializable

@SyncForgeEntity(entityType = "notes")
@Entity(tableName = "notes")
@Serializable
data class NoteEntity(
    @PrimaryKey override val id: String,
    val title: String,
    val body: String = "",
    /** Optional FK to [dev.syncforge.sample.tags.TagEntity] — synced independently. */
    val tagId: String? = null,
    override val localVersion: Long = 0,
    override val updatedAtMillis: Long = System.currentTimeMillis(),
    override val syncState: SyncState = SyncState.SYNCED,
) : SyncedEntity {
    companion object {
        const val ENTITY_TYPE = "notes"
    }
}