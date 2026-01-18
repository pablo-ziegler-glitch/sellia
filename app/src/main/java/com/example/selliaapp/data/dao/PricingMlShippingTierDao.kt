package com.example.selliaapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.selliaapp.data.local.entity.PricingMlShippingTierEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PricingMlShippingTierDao {
    @Query("SELECT * FROM pricing_ml_shipping_tiers ORDER BY maxWeightKg ASC")
    fun observeAll(): Flow<List<PricingMlShippingTierEntity>>

    @Query("SELECT * FROM pricing_ml_shipping_tiers ORDER BY maxWeightKg ASC")
    suspend fun getAllOnce(): List<PricingMlShippingTierEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PricingMlShippingTierEntity): Long

    @Update
    suspend fun update(item: PricingMlShippingTierEntity): Int

    @Query("DELETE FROM pricing_ml_shipping_tiers WHERE id = :id")
    suspend fun deleteById(id: Int): Int
}
