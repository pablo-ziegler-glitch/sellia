package com.floki.app.data.model.dashboard

/**
 * Proyección simple para mostrar alertas de stock bajo en el dashboard.
 */
data class LowStockProduct(
    val id: Int,
    val name: String,
    val quantity: Int,
    val minStock: Int
) {
    /** Diferencia entre el mínimo deseado y el stock disponible. */
    val deficit: Int get() = (minStock - quantity).coerceAtLeast(0)
}
