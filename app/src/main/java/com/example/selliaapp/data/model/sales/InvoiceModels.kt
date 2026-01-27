package com.example.selliaapp.data.model.sales

import java.time.LocalDate

// [NUEVO] Resumen para el listado
data class InvoiceSummary(
    val id: Long,
    val number: String,
    val customerName: String,
    val date: LocalDate,
    val total: Double
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
    val items: List<InvoiceItemRow>,
)
