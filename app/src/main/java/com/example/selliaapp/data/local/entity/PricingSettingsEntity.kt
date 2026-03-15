package com.example.selliaapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "pricing_settings")
data class PricingSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val ivaTerminalPercent: Double,
    /** IVA sobre el precio de venta final (21 % por defecto). Se multiplica sobre el total después de cubrir costos y ganancia objetivo. */
    val ivaProductPercent: Double = 21.0,
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
    val fixedCostImputationMode: String = FixedCostImputationMode.FULL_TO_ALL_PRODUCTS,
    val recalcIntervalMinutes: Int,
    /** Costo que cobra el procesador al vender con Precio de Lista (3 cuotas sin interés), sin IVA. Ej: 12.99 */
    val posnetListaCostPercent: Double = 12.99,
    /** Costo que cobra el procesador al cobrar en el momento (efectivo/transferencia), sin IVA. Ej: 6.60 */
    val cobroEnMomentoCostPercent: Double = 6.60,
    val updatedAt: Instant,
    val updatedBy: String
) {
    object FixedCostImputationMode {
        const val FULL_TO_ALL_PRODUCTS = "FULL_TO_ALL_PRODUCTS"
        const val BY_PRICE_RANGE = "BY_PRICE_RANGE"
    }
}
