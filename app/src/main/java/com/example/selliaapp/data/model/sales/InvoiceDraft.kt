 package com.example.selliaapp.data.model.sales

/**
 * Borrador de factura generado a partir del carrito.
 * Ajustá campos a tu dominio real (cliente, descuentos, etc.).
 */
data class InvoiceDraft(
    val items: List<CartItem>,
    val subtotal: Double,
    val taxes: Double,
    val total: Double,
    val discountPercent: Int = 0,
    val discountAmount: Double = 0.0,
    val surchargePercent: Int = 0,
    val surchargeAmount: Double = 0.0,
    val paymentMethod: String = "",
    val paymentNotes: String? = null,
    val customerId: Long? = null,
    val customerName: String? = null
)