 package com.floki.app.data.model.sales

data class Cart(
    val items: List<CartItem> = emptyList(),
    val subtotal: Double = 0.0,
    val taxes: Double = 0.0,
    val total: Double = 0.0
)

data class CartItem(
    val productId: Long,
    val name: String,
    val quantity: Int,
    val unitPrice: Double
) {
    val lineTotal: Double get() = quantity * unitPrice
}
