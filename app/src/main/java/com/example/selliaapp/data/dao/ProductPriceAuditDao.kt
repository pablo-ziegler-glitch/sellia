package com.example.selliaapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.selliaapp.data.local.entity.ProductPriceAuditEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductPriceAuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ProductPriceAuditEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<ProductPriceAuditEntity>)

    @Query("SELECT * FROM product_price_audit_log ORDER BY changedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<ProductPriceAuditEntity>>
}
