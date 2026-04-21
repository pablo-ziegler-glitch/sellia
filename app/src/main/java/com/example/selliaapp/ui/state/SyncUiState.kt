package com.example.selliaapp.ui.state

import com.example.selliaapp.data.model.sales.SaleBreakdown

/**
 * Item del carrito listo para UI. Guardamos el stock máximo (maxStock) para validar sin ir a DB.
 */
data class CartItemUi(
    val productId: Int,
    val name: String,
    val barcode: String?,
    val unitPrice: Double,
    val listPrice: Double,
    val cashPrice: Double,
    val transferPrice: Double,
    val purchasePrice: Double = 0.0,
    val qty: Int,
    val maxStock: Int
) {
    val lineTotal: Double
        get() = unitPrice * qty
}

/**
 * Estado de la pantalla de venta.
 * - stockViolations: productId -> stock disponible (si qty > disponible).
 * - canCheckout: habilita/deshabilita el botón Vender.
 */
data class SellUiState(
    val items: List<CartItemUi> = emptyList(),
    val subtotal: Double = 0.0,
    val discountPercent: Int = 0,
    val discountAmount: Double = 0.0,
    val manualDiscountAmount: Double = 0.0,
    val customerDiscountPercent: Int = 0,
    val customerDiscountAmount: Double = 0.0,
    val surchargePercent: Int = 0,
    val surchargeAmount: Double = 0.0,
    val total: Double = 0.0,
    /** Mapa de violaciones: productId -> stockDisponible (cuando qty > stock) */
    val stockViolations: Map<Int, Int> = emptyMap(),
    val paymentMethod: PaymentMethod = PaymentMethod.LISTA,
    val paymentNotes: String = "",
    val orderType: OrderType = OrderType.INMEDIATA,
    val selectedCustomerId: Int? = null,
    val selectedCustomerName: String? = "Consumidor Final",
    val customerSummary: CustomerSummaryUi? = null,
    val breakdown: SaleBreakdown? = null
) {
    /** Habilita el checkout si no hay violaciones y hay al menos un ítem. */
    val canCheckout: Boolean
        get() = stockViolations.isEmpty() && items.isNotEmpty()

    val totalDiscountPercent: Int
        get() = (discountPercent + customerDiscountPercent).coerceAtMost(100)
}

enum class PaymentMethod {
    LISTA,
    EFECTIVO,
    TRANSFERENCIA
}

enum class OrderType {
    INMEDIATA,
    RESERVA,
    ENVIO
}

data class CustomerSummaryUi(
    val totalSpent: Double,
    val purchaseCount: Int,
    val lastPurchaseMillis: Long?
)
