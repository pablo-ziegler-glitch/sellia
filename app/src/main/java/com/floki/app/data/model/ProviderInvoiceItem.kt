package com.floki.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ítem de una factura de proveedor.
 * - priceUnit: precio unitario neto (sin IVA).
 * - vatPercent: % IVA (21, 10.5, etc).
 * - vatAmount: monto de IVA (priceUnit * qty * %).
 * - total: total final del renglón (neto + IVA).
 */
@Entity(tableName = "provider_invoice_items")
data class ProviderInvoiceItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val invoiceId: Int,
    val code: String? = null,
    val name: String,
    val quantity: Double,
    val priceUnit: Double,
    val vatPercent: Double,
    val vatAmount: Double,
    val total: Double
)
