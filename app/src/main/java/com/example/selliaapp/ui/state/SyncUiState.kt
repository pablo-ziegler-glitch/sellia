package com.example.selliaapp.ui.state


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
    val promo3x2Enabled: Boolean = true,
    val promo3x2MinQuantity: Int = 3,
    val promo3x2MinSubtotal: Double = 0.0,
    val promoDiscountAmount: Double = 0.0,
    val surchargePercent: Int = 0,
    val surchargeAmount: Double = 0.0,
    val total: Double = 0.0,
    /** Mapa de violaciones: productId -> stockDisponible (cuando qty > stock) */
    val stockViolations: Map<Int, Int> = emptyMap(),
    val paymentMethod: PaymentMethod = PaymentMethod.LISTA,
    val paymentNotes: String = "",
    val orderType: OrderType = OrderType.INMEDIATA,
    val selectedCustomerId: Int? = null,
    val selectedCustomerName: String? = null,
    val customerSummary: CustomerSummaryUi? = null
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
