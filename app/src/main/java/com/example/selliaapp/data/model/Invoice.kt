// File: Invoice.kt
package com.example.selliaapp.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Factura/venta registrada.
 * dateMillis: epoch ms (System.currentTimeMillis())
 * customerId: opcional si no asignás cliente a la venta.
 * total: total de la factura (suma de líneas).
 */

@Entity(tableName = "invoices",
    indices = [
        Index(value = ["dateMillis"]), // ⬅️ CORRECTO: la columna existe
        Index(value = ["customerId"])
    ]
)


data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val dateMillis: Long,
    val customerId: Int?,
    val customerName: String?,
    val subtotal: Double,
    val taxes: Double,
    val discountPercent: Int,
    val discountAmount: Double,
    val surchargePercent: Int,
    val surchargeAmount: Double,
    val total: Double,
    val paymentMethod: String,
    val paymentNotes: String?
)