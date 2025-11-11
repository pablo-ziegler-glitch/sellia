package com.example.selliaapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.selliaapp.data.local.entity.SyncOutboxEntity

@Dao
interface SyncOutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SyncOutboxEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<SyncOutboxEntity>)

    @Query("SELECT * FROM sync_outbox ORDER BY createdAt ASC")
    suspend fun getAll(): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_outbox WHERE entityType = :entityType ORDER BY createdAt ASC")
    suspend fun getByType(entityType: String): List<SyncOutboxEntity>

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
}
