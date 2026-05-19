package com.example.selliaapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.selliaapp.data.local.entity.ProductStateHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductStateHistoryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: ProductStateHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entries: List<ProductStateHistoryEntity>)

    @Query(
        """
        SELECT * FROM product_state_history
        WHERE productUuid = :productUuid
        ORDER BY recordedAtEpochMs DESC
        """
    )
    fun observeByProductUuid(productUuid: String): Flow<List<ProductStateHistoryEntity>>
}
