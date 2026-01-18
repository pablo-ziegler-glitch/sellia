package com.example.selliaapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "pricing_audit_log")
data class PricingAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val scope: String,
    val itemId: Int? = null,
    val field: String,
    val oldValue: String?,
    val newValue: String?,
    val changedAt: Instant,
    val changedBy: String
)
