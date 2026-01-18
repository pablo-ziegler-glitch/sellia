package com.example.selliaapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.selliaapp.data.local.entity.PricingAuditEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PricingAuditDao {
    @Query("SELECT * FROM pricing_audit_log ORDER BY changedAt DESC")
    fun observeAll(): Flow<List<PricingAuditEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: PricingAuditEntity): Long
}
