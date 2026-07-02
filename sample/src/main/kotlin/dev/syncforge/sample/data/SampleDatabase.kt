package dev.syncforge.sample.tasks

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.syncforge.sample.notes.NoteDao
import dev.syncforge.sample.notes.NoteEntity
import dev.syncforge.sample.tags.TagDao
import dev.syncforge.sample.tags.TagEntity

@Database(
    entities = [TaskEntity::class, NoteEntity::class, TagEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class SampleDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao
    abstract fun tagDao(): TagDao

    companion object {
        fun create(context: Context): SampleDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                SampleDatabase::class.java,
                "syncforge_sample_tasks.db",
            ).fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}