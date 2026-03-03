package com.floki.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.floki.app.data.local.entity.TenantSkuConfigEntity

@Dao
interface TenantSkuConfigDao {
    @Query("SELECT * FROM tenant_sku_config WHERE tenantId = :tenantId LIMIT 1")
    suspend fun getByTenantId(tenantId: String): TenantSkuConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: TenantSkuConfigEntity)
}
