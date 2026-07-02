package dev.syncforge.sample.notes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.syncforge.annotations.SyncForgeDao
import kotlinx.coroutines.flow.Flow

@SyncForgeDao(entityClass = "dev.syncforge.sample.notes.NoteEntity")
@Dao
interface NoteDao {

    @Query("SELECT * FROM notes ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)
}