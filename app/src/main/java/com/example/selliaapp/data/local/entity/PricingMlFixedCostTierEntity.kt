package com.example.selliaapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pricing_ml_fixed_cost_tiers")
data class PricingMlFixedCostTierEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val maxPrice: Double,
    val cost: Double
)
