package dev.syncforge.conflict

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ConflictDao {

    @Query("SELECT * FROM syncforge_conflicts ORDER BY detectedAtMillis DESC")
    fun observeAll(): Flow<List<ConflictEntryEntity>>

    @Query(
        """
        SELECT * FROM syncforge_conflicts
        WHERE status = :openStatus
        ORDER BY detectedAtMillis DESC
        """,
    )
    fun observeOpen(openStatus: String = ConflictStatus.OPEN.name): Flow<List<ConflictEntryEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM syncforge_conflicts
        WHERE status = :openStatus
        """,
    )
    suspend fun countOpen(openStatus: String = ConflictStatus.OPEN.name): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: ConflictEntryEntity): Long

    @Update
    suspend fun update(entry: ConflictEntryEntity)

    @Query(
        """
        SELECT * FROM syncforge_conflicts
        WHERE entityType = :entityType AND entityId = :entityId AND status = :openStatus
        LIMIT 1
        """,
    )
    suspend fun findOpen(
        entityType: String,
        entityId: String,
        openStatus: String = ConflictStatus.OPEN.name,
    ): ConflictEntryEntity?

    @Query(
        """
        UPDATE syncforge_conflicts
        SET status = :resolvedStatus, resolutionKind = :resolutionKind
        WHERE id = :id
        """,
    )
    suspend fun markResolved(
        id: Long,
        resolvedStatus: String,
        resolutionKind: String,
    )

    @Query(
        """
        UPDATE syncforge_conflicts
        SET status = :autoResolvedStatus, resolutionKind = :resolutionKind
        WHERE entityType = :entityType AND entityId = :entityId AND status = :openStatus
        """,
    )
    suspend fun closeOpenForEntity(
        entityType: String,
        entityId: String,
        openStatus: String = ConflictStatus.OPEN.name,
        autoResolvedStatus: String = ConflictStatus.AUTO_RESOLVED.name,
        resolutionKind: String,
    )
}