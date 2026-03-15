package com.example.selliaapp.data.model.sales

import java.time.LocalDate

data class InvoiceProfitSummary(
    val invoiceId: Long,
    val dateMillis: Long,
    val total: Double,
    val estimatedCost: Double,
    val estimatedProfit: Double
)

data class DailyProfitSummary(
    val date: LocalDate,
    val totalSales: Double,
    val estimatedCost: Double,
    val estimatedProfit: Double
)
