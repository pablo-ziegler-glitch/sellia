// File: InvoiceItem.kt
package com.floki.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey


/**
 * Ítem de una factura.
 * invoiceId: FK a Invoice.
 * productId: FK lógica a Product (no declaramos FK duro para evitar borrados en cascada accidentales).
 * quantity: cantidad vendida.
 * unitPrice: precio unitario al momento de la venta.
 * lineTotal: unitPrice * quantity (guardado para facilitar reportes).
 */
@Entity(
    tableName = "invoice_items",
    foreignKeys = [
        ForeignKey(
            entity = Invoice::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("invoiceId"), Index("productId")]
    )
data class InvoiceItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val invoiceId: Long,
    val productId: Int,
    val productName: String,
    val quantity: Int,
    val unitPrice: Double,
    val lineTotal: Double

)
