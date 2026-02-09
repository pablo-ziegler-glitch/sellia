package com.example.selliaapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.selliaapp.data.local.entity.CloudServiceConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CloudServiceConfigDao {
    @Query("SELECT * FROM cloud_service_configs ORDER BY ownerEmail")
    fun observeAll(): Flow<List<CloudServiceConfigEntity>>

    @Query("SELECT * FROM cloud_service_configs WHERE ownerEmail = :ownerEmail LIMIT 1")
    suspend fun getByOwnerEmail(ownerEmail: String): CloudServiceConfigEntity?

    @Query("SELECT COUNT(*) FROM cloud_service_configs WHERE cloudEnabled = 1")
    suspend fun countCloudEnabled(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: CloudServiceConfigEntity)
}
