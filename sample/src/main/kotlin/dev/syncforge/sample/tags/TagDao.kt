package dev.syncforge.sample.tags

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.syncforge.annotations.SyncForgeDao
import kotlinx.coroutines.flow.Flow

@SyncForgeDao(entityClass = "dev.syncforge.sample.tags.TagEntity")
@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: TagEntity)

    @Update
    suspend fun update(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: String)
}