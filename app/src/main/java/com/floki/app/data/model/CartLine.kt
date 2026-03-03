package com.floki.app.data.model

import com.floki.app.data.local.entity.ProductEntity


/**
 * Línea del carrito durante la venta.
 * Se usa solo en memoria (no es entidad Room).
 */
data class CartLine(
    val product: ProductEntity,
    val quantity: Int
)
