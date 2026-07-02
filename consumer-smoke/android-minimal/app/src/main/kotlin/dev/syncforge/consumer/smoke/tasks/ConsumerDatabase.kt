package dev.syncforge.consumer.smoke.tasks

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TaskEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ConsumerDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        fun create(context: Context): ConsumerDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ConsumerDatabase::class.java,
                "consumer_smoke.db",
            ).build()
    }
}