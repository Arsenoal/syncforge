package dev.syncforge.sample.tags

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.syncforge.annotations.SyncForgeEntity
import dev.syncforge.entity.SyncedEntity
import dev.syncforge.model.SyncState
import kotlinx.serialization.Serializable

@SyncForgeEntity(entityType = "tags")
@Entity(tableName = "tags")
@Serializable
data class TagEntity(
    @PrimaryKey override val id: String,
    val label: String,
    override val localVersion: Long = 0,
    override val updatedAtMillis: Long = System.currentTimeMillis(),
    override val syncState: SyncState = SyncState.SYNCED,
) : SyncedEntity {
    companion object {
        const val ENTITY_TYPE = "tags"
    }
}