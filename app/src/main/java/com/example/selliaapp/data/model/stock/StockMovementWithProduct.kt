package com.example.selliaapp.data.model.stock

import java.time.Instant

/**
 * Movimiento de stock acompañado del nombre del producto.
 */
data class StockMovementWithProduct(
    val id: Long,
    val productId: Int,
    val productName: String,
    val delta: Int,
    val reason: String,
    val note: String?,
    val ts: Instant
)
