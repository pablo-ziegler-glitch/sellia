package com.example.selliaapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tenant_sku_config")
data class TenantSkuConfigEntity(
    @PrimaryKey
    val tenantId: String,
    val storeName: String,
    val skuPrefix: String,
    val updatedAtEpochMs: Long
)
