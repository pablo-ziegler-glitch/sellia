package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.repository.IProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val DEFAULT_SHARING_TIMEOUT_MS = 5_000L

data class PriceSummaryRow(
    val label: String,
    val total: Double,
    val missingCount: Int
)

data class PriceSummaryState(
    val totalProducts: Int = 0,
    val productsWithStock: Int = 0,
    val totalUnits: Int = 0,
    val rows: List<PriceSummaryRow> = emptyList()
)

@HiltViewModel
class PriceSummaryViewModel @Inject constructor(
    private val productRepository: IProductRepository
) : ViewModel() {

    val state: StateFlow<PriceSummaryState> = productRepository.getProducts()
        .map { products -> buildState(products) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(DEFAULT_SHARING_TIMEOUT_MS),
            initialValue = PriceSummaryState()
        )

    private fun buildState(products: List<ProductEntity>): PriceSummaryState {
        val totalUnits = products.sumOf { it.quantity.coerceAtLeast(0) }
        val productsWithStock = products.count { it.quantity > 0 }
        val rows = listOf(
            buildRow("Precio final", products) { product ->
                product.listPrice ?: product.cashPrice ?: product.transferPrice
            },
            buildRow("Precio lista", products) { product ->
                product.listPrice
            },
            buildRow("Precio efectivo", products) { product ->
                product.cashPrice ?: product.listPrice
            },
            buildRow("Precio transferencia", products) { product ->
                product.transferPrice ?: product.listPrice
            },
            buildRow("Precio transferencia neto", products) { product ->
                product.transferNetPrice
                    ?: product.transferPrice
                    ?: product.listPrice
            },
            buildRow("Precio ML", products) { product ->
                product.mlPrice
            },
            buildRow("Precio ML 3C", products) { product ->
                product.ml3cPrice
            },
            buildRow("Precio ML 6C", products) { product ->
                product.ml6cPrice
            }
        )
        return PriceSummaryState(
            totalProducts = products.size,
            productsWithStock = productsWithStock,
            totalUnits = totalUnits,
            rows = rows
        )
    }

    private fun buildRow(
        label: String,
        products: List<ProductEntity>,
        priceSelector: (ProductEntity) -> Double?
    ): PriceSummaryRow {
        var total = 0.0
        var missingCount = 0
        products.forEach { product ->
            val price = priceSelector(product)
            if (product.quantity > 0) {
                if (price == null) {
                    missingCount += 1
                } else {
                    total += price * product.quantity
                }
            }
        }
        return PriceSummaryRow(label = label, total = total, missingCount = missingCount)
    }
}
