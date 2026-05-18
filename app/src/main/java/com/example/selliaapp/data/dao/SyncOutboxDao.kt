package com.example.selliaapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.selliaapp.data.local.entity.SyncOutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SyncOutboxEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<SyncOutboxEntity>)

    @Query("SELECT * FROM sync_outbox ORDER BY createdAt ASC")
    suspend fun getAll(): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_outbox ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<SyncOutboxEntity>>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM sync_outbox WHERE entityType = :entityType ORDER BY createdAt ASC")
    suspend fun getByType(entityType: String): List<SyncOutboxEntity>

    @Query(
        "SELECT * FROM sync_outbox WHERE entityType = :entityType AND operation = :operation ORDER BY createdAt ASC"
    )
    suspend fun getByTypeAndOperation(entityType: String, operation: String): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_outbox WHERE entityType = :entityType AND entityId = :entityId LIMIT 1")
    suspend fun getByTypeAndId(entityType: String, entityId: Long): SyncOutboxEntity?

    @Query(
        """
        SELECT * FROM sync_outbox
        WHERE entityType = :entityType
          AND entityUuid = :entityUuid
          AND operation = :operation
        LIMIT 1
        """
    )
    suspend fun getByTypeAndUuidAndOperation(
        entityType: String,
        entityUuid: String,
        operation: String
    ): SyncOutboxEntity?

    @Query(
        "DELETE FROM sync_outbox WHERE entityType = :entityType AND entityId IN (:entityIds)"
    )
    suspend fun deleteByTypeAndIds(entityType: String, entityIds: List<Long>)

    @Query(
        "UPDATE sync_outbox SET attempts = attempts + 1, lastAttemptAt = :timestamp, lastError = :error " +
            "WHERE entityType = :entityType AND entityId IN (:entityIds)"
    )
    suspend fun markAttempt(
        entityType: String,
        entityIds: List<Long>,
        timestamp: Long,
        error: String?
    )

    @Query("DELETE FROM sync_outbox WHERE id IN (:rowIds)")
    suspend fun deleteByRowIds(rowIds: List<Long>)
}
