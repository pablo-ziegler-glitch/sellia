package com.example.selliaapp.pricing

import com.example.selliaapp.data.local.entity.PricingFixedCostEntity
import com.example.selliaapp.data.local.entity.PricingMlFixedCostTierEntity
import com.example.selliaapp.data.local.entity.PricingMlShippingTierEntity
import com.example.selliaapp.data.local.entity.PricingSettingsEntity
import kotlin.math.ceil

data class PricingResult(
    val listPrice: Double,
    val cashPrice: Double,
    val transferPrice: Double,
    val transferNetPrice: Double,
    val mlPrice: Double?,
    val ml3cPrice: Double?,
    val ml6cPrice: Double?,
    val fixedCostImputed: Double,
    val fixedCostUnit: Double
)

data class MlPricingResult(
    val ml0: Double,
    val ml3: Double,
    val ml6: Double
)

object PricingCalculator {
    fun calculate(
        purchasePrice: Double,
        settings: PricingSettingsEntity,
        fixedCosts: List<PricingFixedCostEntity>,
        mlFixedCostTiers: List<PricingMlFixedCostTierEntity>,
        mlShippingTiers: List<PricingMlShippingTierEntity>
    ): PricingResult {
        val iva = settings.ivaTerminalPercent / 100.0
        val costTotal = fixedCosts.sumOf { item ->
            val multiplier = if (item.applyIva) 1.0 + iva else 1.0
            item.amount * multiplier
        }
        val salesEstimate = settings.monthlySalesEstimate.coerceAtLeast(1)
        val fixedCostUnit = costTotal / salesEstimate
        val fixedCostImputed = fixedCostUnit * (coefficientFor(purchasePrice, settings) / 100.0)
        val targetMargin = settings.gainTargetPercent / 100.0
        val operativosLocal = settings.operativosLocalPercent / 100.0
        val posnet3Cuotas = settings.posnet3CuotasPercent / 100.0
        val transferenciaRetencion = settings.transferenciaRetencionPercent / 100.0

        val baseWithProfit = (purchasePrice + fixedCostImputed) * (1 + targetMargin)
        val withOperatives = baseWithProfit + (purchasePrice * operativosLocal)
        val listPriceRaw = withOperatives * (1 + posnet3Cuotas)
        val cashPriceRaw = withOperatives
        val transferPriceRaw = withOperatives

        val listPrice = roundUpByTier(listPriceRaw)
        val cashPrice = roundUpByTier(cashPriceRaw)
        val transferPrice = roundUpByTier(transferPriceRaw)
        val transferNetPrice = transferPrice * (1 - transferenciaRetencion)
        val costBase = purchasePrice + fixedCostImputed + (purchasePrice * operativosLocal)
        val mlPricing = calculateMercadoPagoPrices(
            priceLowerBound = listPrice,
            costBase = costBase,
            settings = settings,
            fixedCostTiers = mlFixedCostTiers,
            shippingTiers = mlShippingTiers
        )

        return PricingResult(
            listPrice = listPrice,
            cashPrice = cashPrice,
            transferPrice = transferPrice,
            transferNetPrice = transferNetPrice,
            mlPrice = mlPricing?.ml0,
            ml3cPrice = mlPricing?.ml3,
            ml6cPrice = mlPricing?.ml6,
            fixedCostImputed = fixedCostImputed,
            fixedCostUnit = fixedCostUnit
        )
    }

    fun calculateMercadoLibrePrices(
        priceLowerBound: Double,
        costBase: Double,
        gainMinimum: Double,
        commissionPercent: Double,
        cuotas3Percent: Double,
        cuotas6Percent: Double,
        shippingThreshold: Double,
        weightKg: Double,
        fixedCostTiers: List<PricingMlFixedCostTierEntity>,
        shippingTiers: List<PricingMlShippingTierEntity>
    ): MlPricingResult? {
        val start = roundUp500(maxOf(priceLowerBound, 1000.0))
        val ml0 = findMlPrice(start, costBase, gainMinimum, commissionPercent, 0.0, shippingThreshold, weightKg, fixedCostTiers, shippingTiers)
        val ml3 = findMlPrice(start, costBase, gainMinimum, commissionPercent, cuotas3Percent, shippingThreshold, weightKg, fixedCostTiers, shippingTiers)
        val ml6 = findMlPrice(start, costBase, gainMinimum, commissionPercent, cuotas6Percent, shippingThreshold, weightKg, fixedCostTiers, shippingTiers)

        if (ml0 == null || ml3 == null || ml6 == null) return null
        if (!(ml6 > ml3 && ml3 > ml0)) return null
        if (ml0 == ml3 || ml0 == ml6 || ml3 == ml6) return null
        return MlPricingResult(ml0 = ml0, ml3 = ml3, ml6 = ml6)
    }

    /**
     * Mercado Pago comparte la misma configuración de Mercado Libre en [PricingSettingsEntity].
     *
     * Campos utilizados:
     * - [PricingSettingsEntity.mlCommissionPercent]
     * - [PricingSettingsEntity.mlCuotas3Percent]
     * - [PricingSettingsEntity.mlCuotas6Percent]
     * - [PricingSettingsEntity.mlGainMinimum]
     * - [PricingSettingsEntity.mlShippingThreshold]
     * - [PricingSettingsEntity.mlDefaultWeightKg]
     *
     * Se reutiliza [calculateMercadoLibrePrices] para evitar duplicar lógica mientras la
     * estrategia de comisiones y envíos siga siendo la misma.
     */
    fun calculateMercadoPagoPrices(
        priceLowerBound: Double,
        costBase: Double,
        settings: PricingSettingsEntity,
        fixedCostTiers: List<PricingMlFixedCostTierEntity>,
        shippingTiers: List<PricingMlShippingTierEntity>
    ): MlPricingResult? = calculateMercadoLibrePrices(
        priceLowerBound = priceLowerBound,
        costBase = costBase,
        gainMinimum = settings.mlGainMinimum,
        commissionPercent = settings.mlCommissionPercent,
        cuotas3Percent = settings.mlCuotas3Percent,
        cuotas6Percent = settings.mlCuotas6Percent,
        shippingThreshold = settings.mlShippingThreshold,
        weightKg = settings.mlDefaultWeightKg,
        fixedCostTiers = fixedCostTiers,
        shippingTiers = shippingTiers
    )

    private fun coefficientFor(price: Double, settings: PricingSettingsEntity): Double = when {
        price <= 1500.0 -> settings.coefficient0To1500Percent
        price <= 3000.0 -> settings.coefficient1501To3000Percent
        price <= 5000.0 -> settings.coefficient3001To5000Percent
        price <= 7500.0 -> settings.coefficient5001To7500Percent
        price <= 10000.0 -> settings.coefficient7501To10000Percent
        else -> settings.coefficient10001PlusPercent
    }

    private fun roundUpByTier(value: Double): Double {
        val step = when {
            value <= 1500.0 -> 50.0
            value <= 3000.0 -> 100.0
            value <= 5000.0 -> 200.0
            else -> 500.0
        }
        return ceil(value / step) * step
    }

    private fun roundUp500(value: Double): Double =
        ceil(value / 500.0) * 500.0

    private fun findMlPrice(
        startPrice: Double,
        costBase: Double,
        gainMinimum: Double,
        commissionPercent: Double,
        cuotaPercent: Double,
        shippingThreshold: Double,
        weightKg: Double,
        fixedCostTiers: List<PricingMlFixedCostTierEntity>,
        shippingTiers: List<PricingMlShippingTierEntity>
    ): Double? {
        var candidate = startPrice
        repeat(200) {
            val rounded = roundUp500(candidate)
            val gain = calculateMlGain(
                price = rounded,
                costBase = costBase,
                gainMinimum = gainMinimum,
                commissionPercent = commissionPercent,
                cuotaPercent = cuotaPercent,
                shippingThreshold = shippingThreshold,
                weightKg = weightKg,
                fixedCostTiers = fixedCostTiers,
                shippingTiers = shippingTiers
            )
            if (gain >= gainMinimum) return rounded
            candidate = rounded + 500.0
        }
        return null
    }

    private fun calculateMlGain(
        price: Double,
        costBase: Double,
        gainMinimum: Double,
        commissionPercent: Double,
        cuotaPercent: Double,
        shippingThreshold: Double,
        weightKg: Double,
        fixedCostTiers: List<PricingMlFixedCostTierEntity>,
        shippingTiers: List<PricingMlShippingTierEntity>
    ): Double {
        val commission = price * (commissionPercent / 100.0)
        val cuotas = price * (cuotaPercent / 100.0)
        val fixedCost = fixedCostFor(price, fixedCostTiers)
        val shipping = if (price < shippingThreshold) 0.0 else shippingCostFor(weightKg, shippingTiers)
        val total = commission + cuotas + fixedCost + shipping
        val neto = price - total
        return neto - costBase
    }

    private fun fixedCostFor(price: Double, tiers: List<PricingMlFixedCostTierEntity>): Double {
        val tier = tiers.sortedBy { it.maxPrice }.firstOrNull { price <= it.maxPrice }
        return tier?.cost ?: 0.0
    }

    private fun shippingCostFor(weightKg: Double, tiers: List<PricingMlShippingTierEntity>): Double {
        val tier = tiers.sortedBy { it.maxWeightKg }.firstOrNull { weightKg <= it.maxWeightKg }
        return tier?.cost ?: (tiers.maxByOrNull { it.maxWeightKg }?.cost ?: 0.0)
    }
}
