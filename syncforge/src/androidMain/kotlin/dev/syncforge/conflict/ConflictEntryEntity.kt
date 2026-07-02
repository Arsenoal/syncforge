package dev.syncforge.conflict

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "syncforge_conflicts",
    indices = [
        Index(value = ["entityType", "entityId"]),
        Index(value = ["status"]),
    ],
)
internal data class ConflictEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val entityId: String,
    val localJson: String,
    val remoteJson: String?,
    val localUpdatedAtMillis: Long,
    val remoteServerVersion: Long,
    val remoteUpdatedAtMillis: Long,
    val detectedAtMillis: Long,
    val status: String,
    val resolutionKind: String?,
)