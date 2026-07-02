package dev.syncforge.outbox

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.syncforge.conflict.ConflictDao
import dev.syncforge.conflict.ConflictEntryEntity

@Database(
    entities = [OutboxEntryEntity::class, ConflictEntryEntity::class],
    version = 3,
    exportSchema = false,
)
internal abstract class SyncForgeDatabase : RoomDatabase() {
    abstract fun outboxDao(): OutboxDao
    abstract fun conflictDao(): ConflictDao
}