package com.example.selliaapp.pricing

import com.example.selliaapp.data.local.entity.PricingFixedCostEntity
import com.example.selliaapp.data.local.entity.PricingSettingsEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class PricingCalculatorValidationTest {

    @Test
    fun `con variables de excel purchasePrice 200 devuelve lista 1300 y efectivo 1200`() {
        val settings = baseSettings(monthlySalesEstimate = 500)
        val fixedCosts = fixedCostsFromReference()

        val result = PricingCalculator.calculate(
            purchasePrice = 200.0,
            settings = settings,
            fixedCosts = fixedCosts,
            mlFixedCostTiers = emptyList(),
            mlShippingTiers = emptyList()
        )

        assertThat(result.fixedCostUnit).isWithin(0.01).of(3761.64)
        assertThat(result.fixedCostImputed).isWithin(0.01).of(564.25)
        assertThat(result.cashPrice).isEqualTo(1200.0)
        assertThat(result.listPrice).isEqualTo(1300.0)
    }

    @Test
    fun `con mismas variables salvo ventas mensuales 306 el resultado sube a lista 1900 y efectivo 1700`() {
        val settings = baseSettings(monthlySalesEstimate = 306)
        val fixedCosts = fixedCostsFromReference()

        val result = PricingCalculator.calculate(
            purchasePrice = 200.0,
            settings = settings,
            fixedCosts = fixedCosts,
            mlFixedCostTiers = emptyList(),
            mlShippingTiers = emptyList()
        )

        assertThat(result.cashPrice).isEqualTo(1700.0)
        assertThat(result.listPrice).isEqualTo(1900.0)
    }

    private fun baseSettings(monthlySalesEstimate: Int): PricingSettingsEntity = PricingSettingsEntity(
        id = 1,
        ivaTerminalPercent = 21.0,
        monthlySalesEstimate = monthlySalesEstimate,
        operativosLocalPercent = 3.0,
        posnet3CuotasPercent = 12.22,
        transferenciaRetencionPercent = 5.0,
        gainTargetPercent = 50.0,
        mlCommissionPercent = 15.5,
        mlCuotas3Percent = 8.2,
        mlCuotas6Percent = 12.7,
        mlGainMinimum = 15.0,
        mlShippingThreshold = 10000.0,
        mlDefaultWeightKg = 0.3,
        coefficient0To1500Percent = 15.0,
        coefficient1501To3000Percent = 25.0,
        coefficient3001To5000Percent = 40.0,
        coefficient5001To7500Percent = 60.0,
        coefficient7501To10000Percent = 80.0,
        coefficient10001PlusPercent = 100.0,
        recalcIntervalMinutes = 30,
        updatedAt = Instant.now(),
        updatedBy = "test"
    )

    private fun fixedCostsFromReference(): List<PricingFixedCostEntity> = listOf(
        PricingFixedCostEntity(id = 1, name = "Alquiler", amount = 350000.0),
        PricingFixedCostEntity(id = 2, name = "Sueldos", amount = 1000000.0),
        PricingFixedCostEntity(id = 3, name = "Internet", amount = 80000.0),
        PricingFixedCostEntity(id = 4, name = "Luz", amount = 100000.0),
        PricingFixedCostEntity(id = 5, name = "Monotributo", amount = 100000.0),
        PricingFixedCostEntity(id = 6, name = "Insumos", amount = 200000.0),
        PricingFixedCostEntity(
            id = 7,
            name = "Terminal Posnet",
            amount = 41999.0,
            applyIva = true
        )
    )
}
