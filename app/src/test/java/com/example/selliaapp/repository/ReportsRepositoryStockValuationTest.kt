package com.example.selliaapp.repository

import com.example.selliaapp.data.local.entity.ProductEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportsRepositoryStockValuationTest {

    @Test
    fun buildStockValuationReportCalculatesExpectedProfitPerScenario() {
        val products = listOf(
            ProductEntity(
                id = 1,
                name = "Producto A",
                quantity = 10,
                purchasePrice = 100.0,
                listPrice = 150.0,
                cashPrice = 140.0,
                transferPrice = 145.0,
                transferNetPrice = 140.0,
            ),
            ProductEntity(
                id = 2,
                name = "Producto B",
                quantity = 5,
                purchasePrice = 50.0,
                listPrice = 120.0,
                cashPrice = 100.0,
                transferPrice = 110.0,
                transferNetPrice = 100.0,
            ),
            ProductEntity(
                id = 3,
                name = "Sin costo",
                quantity = 2,
                purchasePrice = null,
                listPrice = 80.0,
                cashPrice = 70.0,
            )
        )

        val report = ReportsRepository.buildStockValuationReport(products)

        assertEquals(3, report.totalProductsWithStock)
        assertEquals(17, report.totalUnitsWithStock)
        assertEquals(1250.0, report.totalAcquisitionCost, 0.01)
        assertEquals(15, report.unitsWithAcquisitionCost)

        val listScenario = report.scenarios.first { it.label == "Precio de lista" }
        assertEquals(2300.0, listScenario.potentialRevenue, 0.01)
        assertEquals(2100.0, listScenario.revenueWithKnownCost, 0.01)
        assertEquals(1250.0, listScenario.acquisitionCost, 0.01)
        assertEquals(850.0, listScenario.expectedProfit, 0.01)
        assertEquals(17, listScenario.unitsWithPrice)
        assertEquals(15, listScenario.unitsWithKnownCost)
    }

    @Test
    fun buildStockValuationReportOmitsScenarioWithoutConfiguredPrices() {
        val products = listOf(
            ProductEntity(
                id = 1,
                name = "Solo contado",
                quantity = 4,
                purchasePrice = 20.0,
                cashPrice = 40.0,
            )
        )

        val report = ReportsRepository.buildStockValuationReport(products)

        assertTrue(report.scenarios.any { it.label == "Precio contado" })
        assertTrue(report.scenarios.none { it.label == "Mercado Libre" })
    }
}
