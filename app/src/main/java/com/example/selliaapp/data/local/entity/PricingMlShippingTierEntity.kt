package com.example.selliaapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pricing_ml_shipping_tiers")
data class PricingMlShippingTierEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val maxWeightKg: Double,
    val cost: Double
)
