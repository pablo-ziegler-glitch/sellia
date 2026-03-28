package com.example.selliaapp.data.model


import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Factura/Boleta de proveedor.
 * - status: IMPAGA / PAGA.
 * - paymentRef: referencia/ID de pago ingresada al marcar pago.
 * - paymentAmount: monto pagado (puede ser parcial o total, en esta primera versión lo tomamos como total).
 * - paymentDateMillis: fecha de pago (epoch millis) -> se usa la fecha actual al marcar pago.
 */
@Entity(tableName = "provider_invoices")
data class ProviderInvoice(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val providerId: Int,
    val number: String,                 // nro de factura/boleta
    val issueDateMillis: Long,          // fecha emisión
    val total: Double,                  // total de factura
    val status: ProviderInvoiceStatus = ProviderInvoiceStatus.IMPAGA,
    val receptionStatus: ProviderInvoiceReceptionStatus = ProviderInvoiceReceptionStatus.PENDING,
    val receivedAtMillis: Long? = null,
    val discrepancyNote: String? = null,
    val paymentRef: String? = null,
    val paymentAmount: Double? = null,
    val paymentDateMillis: Long? = null
)

enum class ProviderInvoiceStatus { IMPAGA, PAGA }
enum class ProviderInvoiceReceptionStatus { PENDING, PARTIAL, RECEIVED }
