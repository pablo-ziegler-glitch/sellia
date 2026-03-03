 package com.floki.app.repository

import kotlinx.coroutines.flow.Flow

 /**
 * Abstracción de carrito.
 * Tu implementación real debería conectar con Room/DAO o memoria compartida.
 */
interface CartRepository {
    data class CartLine(
        val productId: Long,
        val name: String,
        val unitPrice: Double,
        val quantity: Int
    ) {
        val lineTotal: Double get() = unitPrice * quantity
    }

    fun observeCart(): Flow<List<CartLine>>
    suspend fun add(productId: Long, name: String, unitPrice: Double, quantity: Int = 1)
    suspend fun remove(productId: Long, quantity: Int = 1)
    suspend fun clear()
    suspend fun total(): Double
}
