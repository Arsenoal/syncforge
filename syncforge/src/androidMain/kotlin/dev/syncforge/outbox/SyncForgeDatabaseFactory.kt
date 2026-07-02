package dev.syncforge.outbox

import android.content.Context
import androidx.room.Room
import dev.syncforge.conflict.ConflictStore
import dev.syncforge.conflict.RoomConflictStore

internal object SyncForgeDatabaseFactory {

    fun create(context: Context, name: String = "syncforge_outbox.db"): SyncForgeDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            SyncForgeDatabase::class.java,
            name,
        ).fallbackToDestructiveMigration().build()

    fun createInMemory(context: Context): SyncForgeDatabase =
        Room.inMemoryDatabaseBuilder(
            context.applicationContext,
            SyncForgeDatabase::class.java,
        ).fallbackToDestructiveMigration().build()

    fun createOutboxRepository(context: Context, maxRetries: Int = 5): RoomOutboxRepository =
        RoomOutboxRepository(create(context).outboxDao(), maxRetries = maxRetries)

    fun createConflictStore(context: Context): ConflictStore =
        RoomConflictStore(create(context).conflictDao())
}