package com.example.selliaapp.data.model.sales

import java.time.LocalDate
import com.example.selliaapp.data.model.InvoiceStatus

// [NUEVO] Resumen para el listado
data class InvoiceSummary(
    val id: Long,
    val number: String,
    val customerName: String,
    val date: LocalDate,
    val total: Double,
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)

// [NUEVO] Item del detalle
data class InvoiceItemRow(
    val productId: Long,
    val name: String,
    val quantity: Int,
    val unitPrice: Double
) {
    val lineTotal: Double get() = unitPrice * quantity
}

// [NUEVO] Detalle completo
data class InvoiceDetail(
    val id: Long,
    val number: String,
    val customerName: String,
    val date: LocalDate,
    val subtotal: Double,
    val taxes: Double,
    val discountPercent: Int,
    val discountAmount: Double,
    val surchargePercent: Int,
    val surchargeAmount: Double,
    val total: Double,
    val paymentMethod: String,
    val paymentNotes: String?,
    val status: InvoiceStatus,
    val canceledReason: String?,
    val items: List<InvoiceItemRow>,
    val notes: String? = null,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    // Desglose interno (solo visible para ADMIN/OWNER)
    val breakdown: SaleBreakdown? = null
)

/** Fila para el listado del reporte de ganancias */
data class InvoiceProfitSummary(
    val id: Long,
    val number: String,
    val customerName: String,
    val date: LocalDate,
    val total: Double,
    val purchaseCost: Double?,
    val grossProfit: Double?,
    val netGain: Double?,
    val paymentMethod: String
)

/** Resumen diario de ganancias */
data class DailyProfitSummary(
    val date: LocalDate,
    val saleCount: Int,
    val totalRevenue: Double,
    val totalPurchaseCost: Double?,
    val totalGrossProfit: Double?,
    val totalNetGain: Double?
)

enum class SyncStatus {
    SYNCED,
    PENDING,
    ERROR
}
