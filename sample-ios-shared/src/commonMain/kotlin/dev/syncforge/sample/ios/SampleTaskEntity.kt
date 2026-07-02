package dev.syncforge.sample.ios

import dev.syncforge.entity.SyncedEntity
import dev.syncforge.model.SyncState
import kotlinx.serialization.Serializable

@Serializable
data class SampleTaskEntity(
    override val id: String,
    val title: String,
    val completed: Boolean = false,
    override val localVersion: Long = 0,
    override val updatedAtMillis: Long = 0,
    override val syncState: SyncState = SyncState.SYNCED,
) : SyncedEntity {
    companion object {
        const val ENTITY_TYPE: String = "tasks"
    }
}