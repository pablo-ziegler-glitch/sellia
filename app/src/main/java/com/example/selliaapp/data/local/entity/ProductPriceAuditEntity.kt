package com.example.selliaapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "product_price_audit_log",
    indices = [
        Index(value = ["productId"]),
        Index(value = ["changedAt"])
    ]
)
data class ProductPriceAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Int,
    val productName: String,
    val purchasePrice: Double?,
    val oldListPrice: Double?,
    val newListPrice: Double?,
    val oldCashPrice: Double?,
    val newCashPrice: Double?,
    val oldTransferPrice: Double?,
    val newTransferPrice: Double?,
    val oldMlPrice: Double?,
    val newMlPrice: Double?,
    val oldMl3cPrice: Double?,
    val newMl3cPrice: Double?,
    val oldMl6cPrice: Double?,
    val newMl6cPrice: Double?,
    val reason: String,
    val changedBy: String,
    val source: String,
    val changedAt: Instant
)
