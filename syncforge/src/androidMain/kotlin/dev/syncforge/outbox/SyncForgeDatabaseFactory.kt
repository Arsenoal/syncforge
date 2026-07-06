package dev.syncforge.outbox

import android.content.Context
import androidx.room.Room

internal object SyncForgeDatabaseFactory {

    fun create(context: Context, name: String = "syncforge_outbox.db"): SyncForgeDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            SyncForgeDatabase::class.java,
            name,
        ).fallbackToDestructiveMigration(dropAllTables = true).build()

    fun createInMemory(context: Context): SyncForgeDatabase =
        Room.inMemoryDatabaseBuilder(
            context.applicationContext,
            SyncForgeDatabase::class.java,
        ).fallbackToDestructiveMigration(dropAllTables = true).build()
}