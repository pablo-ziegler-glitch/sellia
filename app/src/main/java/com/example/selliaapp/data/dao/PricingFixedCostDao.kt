package com.example.selliaapp.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.selliaapp.data.local.entity.PricingFixedCostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PricingFixedCostDao {
    @Query("SELECT * FROM pricing_fixed_costs ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<PricingFixedCostEntity>>

    @Query("SELECT * FROM pricing_fixed_costs ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllOnce(): List<PricingFixedCostEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PricingFixedCostEntity): Long

    @Update
    suspend fun update(item: PricingFixedCostEntity): Int

    @Delete
    suspend fun delete(item: PricingFixedCostEntity): Int

    @Query("DELETE FROM pricing_fixed_costs WHERE id = :id")
    suspend fun deleteById(id: Int): Int
}
