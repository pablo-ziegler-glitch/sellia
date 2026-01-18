package com.example.selliaapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pricing_fixed_costs")
data class PricingFixedCostEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String? = null,
    val amount: Double,
    val applyIva: Boolean = false
)
