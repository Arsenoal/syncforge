package dev.syncforge.outbox

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
internal interface OutboxDao {

    @Query(
        """
        SELECT COUNT(*) FROM syncforge_outbox
        WHERE retryCount < :maxRetries
        """,
    )
    fun observeAwaitingCount(maxRetries: Int): Flow<Int>

    @Query(
        """
        SELECT * FROM syncforge_outbox
        WHERE retryCount < :maxRetries
        ORDER BY createdAtMillis ASC
        """,
    )
    fun observeAwaiting(maxRetries: Int): Flow<List<OutboxEntryEntity>>

    @Query("SELECT * FROM syncforge_outbox ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<OutboxEntryEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM syncforge_outbox
        WHERE retryCount < :maxRetries
        """,
    )
    suspend fun countAwaiting(maxRetries: Int): Int

    @Query(
        """
        SELECT COUNT(*) FROM syncforge_outbox
        WHERE retryCount >= :maxRetries
        """,
    )
    suspend fun countPermanentlyFailed(maxRetries: Int): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: OutboxEntryEntity): Long

    @Query(
        """
        SELECT * FROM syncforge_outbox
        WHERE retryCount < :maxRetries
        AND (
            lastError IS NULL
            OR (nextRetryAtMillis IS NOT NULL AND nextRetryAtMillis <= :nowMillis)
        )
        ORDER BY createdAtMillis ASC
        LIMIT :limit
        """,
    )
    suspend fun peekReady(limit: Int, nowMillis: Long, maxRetries: Int): List<OutboxEntryEntity>

    @Query("SELECT * FROM syncforge_outbox WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): OutboxEntryEntity?

    @Query("DELETE FROM syncforge_outbox WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Update
    suspend fun update(entry: OutboxEntryEntity)

    @Query(
        """
        SELECT MIN(nextRetryAtMillis) FROM syncforge_outbox
        WHERE retryCount < :maxRetries
        AND nextRetryAtMillis IS NOT NULL
        AND nextRetryAtMillis > :nowMillis
        """,
    )
    suspend fun earliestFutureRetryAtMillis(nowMillis: Long, maxRetries: Int): Long?

    @Query("DELETE FROM syncforge_outbox")
    suspend fun clearAll()
}