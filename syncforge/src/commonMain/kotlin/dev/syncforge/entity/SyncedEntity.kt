package dev.syncforge.entity

import dev.syncforge.model.SyncState

/**
 * Contract for entities managed by SyncForge.
 *
 * Room entities should implement this interface (or embed equivalent columns)
 * and include sync metadata columns:
 * - `localVersion` — monotonically increasing per-row version
 * - `updatedAtMillis` — last local mutation timestamp (used for delta sync + LWW)
 * - `syncState` — current [SyncState]
 *
 * Example (Room):
 * ```
 * @Entity(tableName = "tasks")
 * data class TaskEntity(
 *     @PrimaryKey override val id: String,
 *     val title: String,
 *     override val localVersion: Long = 0,
 *     override val updatedAtMillis: Long = System.currentTimeMillis(),
 *     override val syncState: SyncState = SyncState.PENDING,
 * ) : SyncedEntity
 * ```
 */
interface SyncedEntity {
    val id: String
    val localVersion: Long
    val updatedAtMillis: Long
    val syncState: SyncState
}

/**
 * Server-side metadata attached to pulled deltas.
 */
data class RemoteMetadata(
    val serverVersion: Long,
    val updatedAtMillis: Long,
    val isDeleted: Boolean = false,
)