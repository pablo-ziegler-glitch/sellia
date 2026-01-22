package com.example.selliaapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "pricing_settings")
data class PricingSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val ivaTerminalPercent: Double,
    val monthlySalesEstimate: Int,
    val operativosLocalPercent: Double,
    val posnet3CuotasPercent: Double,
    val transferenciaRetencionPercent: Double,
    val gainTargetPercent: Double,
    val mlCommissionPercent: Double,
    val mlCuotas3Percent: Double,
    val mlCuotas6Percent: Double,
    val mlGainMinimum: Double,
    val mlShippingThreshold: Double,
    val mlDefaultWeightKg: Double,
    val coefficient0To1500Percent: Double,
    val coefficient1501To3000Percent: Double,
    val coefficient3001To5000Percent: Double,
    val coefficient5001To7500Percent: Double,
    val coefficient7501To10000Percent: Double,
    val coefficient10001PlusPercent: Double,
    val recalcIntervalMinutes: Int,
    val updatedAt: Instant,
    val updatedBy: String
)