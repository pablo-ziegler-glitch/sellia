 package com.floki.app.data.model.sales

/**
 * Resultado al confirmar la factura/pedido/venta.
 * Ajustá campos a tu dominio real (id, número, fecha, etc.).
 */
data class InvoiceResult(
    val invoiceId: Long,
    val invoiceNumber: String
)
