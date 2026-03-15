package com.example.selliaapp.data.model.sales

/**
 * Desglose de cargos para ventas al precio de lista.
 * Muestra cuánto del precio cobrado corresponde a comisiones, costos y ganancia neta.
 *
 * Cálculo:
 *   posnetFeeAmount  = grossAmount * posnetRate / (1 + posnetRate)  [fee embebido en precio lista]
 *   operativosFeeAmount = purchaseCostTotal * operativosRate
 *   estimatedNetGain = grossAmount - posnetFeeAmount - purchaseCostTotal - operativosFeeAmount
 */
data class SaleBreakdown(
    val grossAmount: Double,
    val posnetFeeAmount: Double,
    val posnetFeePercent: Double,
    val purchaseCostTotal: Double,
    val operativosFeeAmount: Double,
    val operativosFeePercent: Double,
    val estimatedNetGain: Double
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "grossAmount" to grossAmount,
        "posnetFeeAmount" to posnetFeeAmount,
        "posnetFeePercent" to posnetFeePercent,
        "purchaseCostTotal" to purchaseCostTotal,
        "operativosFeeAmount" to operativosFeeAmount,
        "operativosFeePercent" to operativosFeePercent,
        "estimatedNetGain" to estimatedNetGain
    )
}
