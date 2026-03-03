 package com.floki.app.repository.impl

import com.floki.app.data.model.sales.Cart
import com.floki.app.di.IoDispatcher
import com.floki.app.repository.CartRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

 /**
 * Implementación mínima en memoria para destrabar compilación/flujo.
 * Reemplazá con versión atada a Room si querés persistencia.
 */
@Singleton
class CartRepositoryImpl @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher
) : CartRepository {

    private val state = MutableStateFlow(Cart())

    private val lines = MutableStateFlow<Map<Long, CartRepository.CartLine>>(emptyMap())

    override fun observeCart(): Flow<List<CartRepository.CartLine>> =
        lines.map { it.values.sortedBy { l -> l.productId } }

    override suspend fun add(productId: Long, name: String, unitPrice: Double, quantity: Int) =
        withContext(io) {
            val current = lines.value.toMutableMap()
            val existing = current[productId]
            val newQty = (existing?.quantity ?: 0) + quantity
            current[productId] = CartRepository.CartLine(
                productId = productId,
                name = name,
                unitPrice = unitPrice,
                quantity = newQty
            )
            lines.value = current
        }

    override suspend fun remove(productId: Long, quantity: Int) = withContext(io) {
        val current = lines.value.toMutableMap()
        val existing = current[productId] ?: return@withContext
        val newQty = existing.quantity - quantity
        if (newQty > 0) {
            current[productId] = existing.copy(quantity = newQty)
        } else {
            current.remove(productId)
        }
        lines.value = current
    }

    override suspend fun clear() = withContext(io) {
        lines.value = emptyMap()
    }

    override suspend fun total(): Double = withContext(io) {
        lines.value.values.sumOf { it.lineTotal }
    }


}
