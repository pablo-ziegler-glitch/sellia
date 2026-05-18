package com.example.selliaapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.selliaapp.data.local.entity.ProductSyncConflictEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductSyncConflictDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conflict: ProductSyncConflictEntity): Long

    @Query("SELECT * FROM product_sync_conflicts ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<ProductSyncConflictEntity>>

    @Query(
        """
        UPDATE product_sync_conflicts
        SET resolutionStatus = :resolutionStatus,
            resolvedAtEpochMs = :resolvedAtEpochMs
        WHERE id = :id
        """
    )
    suspend fun markResolved(
        id: Long,
        resolutionStatus: String,
        resolvedAtEpochMs: Long = System.currentTimeMillis()
    ): Int
}
