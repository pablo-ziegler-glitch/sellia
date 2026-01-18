package com.example.selliaapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.selliaapp.data.local.entity.PricingMlFixedCostTierEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PricingMlFixedCostTierDao {
    @Query("SELECT * FROM pricing_ml_fixed_cost_tiers ORDER BY maxPrice ASC")
    fun observeAll(): Flow<List<PricingMlFixedCostTierEntity>>

    @Query("SELECT * FROM pricing_ml_fixed_cost_tiers ORDER BY maxPrice ASC")
    suspend fun getAllOnce(): List<PricingMlFixedCostTierEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PricingMlFixedCostTierEntity): Long

    @Update
    suspend fun update(item: PricingMlFixedCostTierEntity): Int

    @Query("DELETE FROM pricing_ml_fixed_cost_tiers WHERE id = :id")
    suspend fun deleteById(id: Int): Int
}
