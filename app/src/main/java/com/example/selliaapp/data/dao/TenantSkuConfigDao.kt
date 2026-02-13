package com.example.selliaapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.selliaapp.data.local.entity.TenantSkuConfigEntity

@Dao
interface TenantSkuConfigDao {
    @Query("SELECT * FROM tenant_sku_config WHERE tenantId = :tenantId LIMIT 1")
    suspend fun getByTenantId(tenantId: String): TenantSkuConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: TenantSkuConfigEntity)
}
