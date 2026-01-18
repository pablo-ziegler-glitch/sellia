package com.example.selliaapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.selliaapp.data.local.entity.PricingSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PricingSettingsDao {
    @Query("SELECT * FROM pricing_settings WHERE id = 1")
    fun observe(): Flow<PricingSettingsEntity?>

    @Query("SELECT * FROM pricing_settings WHERE id = 1")
    suspend fun getOnce(): PricingSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: PricingSettingsEntity): Long

    @Update
    suspend fun update(settings: PricingSettingsEntity): Int
}
