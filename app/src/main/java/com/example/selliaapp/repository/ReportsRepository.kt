package com.example.selliaapp.repository

import com.example.selliaapp.data.dao.InvoiceDao
import com.example.selliaapp.data.dao.ProductDao
import com.example.selliaapp.data.dao.ReportDataDao
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.model.ReportPoint
import com.example.selliaapp.viewmodel.ReportsFilter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arma datos para los reportes a partir de InvoiceRepository.
 */
data class SalesReport(
    val total: Double,
    val series: List<Pair<String, Double>> // etiqueta (fecha) -> monto
)

data class StockValuationScenario(
    val label: String,
    val potentialRevenue: Double,
    val revenueWithKnownCost: Double,
    val acquisitionCost: Double,
    val expectedProfit: Double,
    val unitsWithPrice: Int,
    val unitsWithKnownCost: Int,
)

data class StockValuationReport(
    val totalProductsWithStock: Int,
    val totalUnitsWithStock: Int,
    val totalAcquisitionCost: Double,
    val unitsWithAcquisitionCost: Int,
    val scenarios: List<StockValuationScenario>,
)

@Singleton
class ReportsRepository @Inject constructor(
    private val invoiceDao: InvoiceDao,
    private val productDao: ProductDao,
    private val reportDataDao: ReportDataDao? = null
) {

    suspend fun getSalesSeries(
        from: LocalDate,
        to: LocalDate,
        bucket: String
    ): List<ReportPoint> {
        val zone = ZoneId.systemDefault()
        val startMillis = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = to.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()

        return if (bucket == "HOUR") {
            val rows = invoiceDao.salesGroupedByHour(startMillis, endMillis)
            val hourFmt = DateTimeFormatter.ofPattern("HH:mm")
            rows.map { row ->
                val ldt = Instant.ofEpochMilli(row.hour).atZone(zone).toLocalDateTime()
                ReportPoint(
                    label = ldt.format(hourFmt),
                    amount = row.total,
                    dateTime = ldt
                )
            }
        } else {
            val rows = invoiceDao.salesGroupedByDay(startMillis, endMillis)
            rows.map { row ->
                val date = Instant.ofEpochMilli(row.day).atZone(zone).toLocalDate()
                ReportPoint(
                    label = date.toString(),
                    amount = row.total,
                    date = date
                )
            }
        }
    }

    /**
     * API friendly para la UI: decide bucket según filtro.
     */
    suspend fun getSalesSeries(
        from: LocalDate,
        to: LocalDate,
        filter: ReportsFilter
    ): List<ReportPoint> {
        val bucket = when (filter) {
            ReportsFilter.DAY -> "HOUR"
            ReportsFilter.WEEK -> "DAY"
            ReportsFilter.MONTH -> "DAY"
        }
        return getSalesSeries(from, to, bucket)
    }

    suspend fun getStockValuationReport(): StockValuationReport {
        val products = productDao.getAllOnce()
        return buildStockValuationReport(products)
    }

    companion object {
        internal fun buildStockValuationReport(products: List<ProductEntity>): StockValuationReport {
            val withStock = products.filter { it.quantity > 0 }
            val totalUnitsWithStock = withStock.sumOf { it.quantity }
            val totalAcquisitionCost = withStock.sumOf { product ->
                val cost = product.purchasePrice?.takeIf { it > 0.0 } ?: 0.0
                product.quantity * cost
            }
            val unitsWithAcquisitionCost = withStock.sumOf { product ->
                if ((product.purchasePrice ?: 0.0) > 0.0) product.quantity else 0
            }

            val scenarios = listOf(
                "Precio de lista" to { p: ProductEntity -> p.listPrice },
                "Precio contado" to { p: ProductEntity -> p.cashPrice },
                "Precio transferencia" to { p: ProductEntity -> p.transferPrice },
                "Transferencia neto" to { p: ProductEntity -> p.transferNetPrice },
                "Mercado Libre" to { p: ProductEntity -> p.mlPrice },
                "Mercado Libre 3 cuotas" to { p: ProductEntity -> p.ml3cPrice },
                "Mercado Libre 6 cuotas" to { p: ProductEntity -> p.ml6cPrice },
            ).map { (label, selector) ->
                buildScenario(label = label, products = withStock, priceSelector = selector)
            }.filter { it.unitsWithPrice > 0 }

            return StockValuationReport(
                totalProductsWithStock = withStock.size,
                totalUnitsWithStock = totalUnitsWithStock,
                totalAcquisitionCost = totalAcquisitionCost,
                unitsWithAcquisitionCost = unitsWithAcquisitionCost,
                scenarios = scenarios
            )
        }

        private fun buildScenario(
            label: String,
            products: List<ProductEntity>,
            priceSelector: (ProductEntity) -> Double?
        ): StockValuationScenario {
            var potentialRevenue = 0.0
            var revenueWithKnownCost = 0.0
            var acquisitionCost = 0.0
            var unitsWithPrice = 0
            var unitsWithKnownCost = 0

            products.forEach { product ->
                val price = priceSelector(product)?.takeIf { it > 0.0 } ?: return@forEach
                val quantity = product.quantity
                potentialRevenue += quantity * price
                unitsWithPrice += quantity

                val purchase = product.purchasePrice?.takeIf { it > 0.0 }
                if (purchase != null) {
                    revenueWithKnownCost += quantity * price
                    acquisitionCost += quantity * purchase
                    unitsWithKnownCost += quantity
                }
            }

            return StockValuationScenario(
                label = label,
                potentialRevenue = potentialRevenue,
                revenueWithKnownCost = revenueWithKnownCost,
                acquisitionCost = acquisitionCost,
                expectedProfit = revenueWithKnownCost - acquisitionCost,
                unitsWithPrice = unitsWithPrice,
                unitsWithKnownCost = unitsWithKnownCost,
            )
        }
    }
}
