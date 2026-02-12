package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.dao.VariantDao
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.local.entity.VariantEntity
import com.example.selliaapp.domain.product.ProductFilterParams
import com.example.selliaapp.domain.product.ProductSortOption
import com.example.selliaapp.domain.product.filterAndSortProducts
import com.example.selliaapp.repository.IProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManageProductsUiState(
    val query: String = "",
    val parentCategory: String = "",
    val category: String = "",
    val color: String = "",
    val size: String = "",
    val minPrice: String = "",
    val maxPrice: String = "",
    val sort: ProductSortOption = ProductSortOption.UPDATED_DESC,
    val onlyLowStock: Boolean = false,
    val onlyNoImage: Boolean = false,
    val onlyNoBarcode: Boolean = false
)

@HiltViewModel
class ManageProductsViewModel @Inject constructor(
    private val repo: IProductRepository,
    private val variantDao: VariantDao
) : ViewModel() {

    private val _state = MutableStateFlow(ManageProductsUiState())
    val state: StateFlow<ManageProductsUiState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val productsAll: Flow<List<ProductEntity>> = repo.observeAll()

    val filteredProducts: Flow<List<ProductEntity>> = combine(productsAll, state) { products, uiState ->
        filterAndSortProducts(products, uiState.toFilterParams())
    }

    fun setQuery(value: String) = _state.update { it.copy(query = value) }
    fun setParentCategory(value: String) = _state.update { it.copy(parentCategory = value) }
    fun setCategory(value: String) = _state.update { it.copy(category = value) }
    fun setColor(value: String) = _state.update { it.copy(color = value) }
    fun setSize(value: String) = _state.update { it.copy(size = value) }
    fun setMinPrice(value: String) = _state.update { it.copy(minPrice = value) }
    fun setMaxPrice(value: String) = _state.update { it.copy(maxPrice = value) }
    fun setSort(sort: ProductSortOption) = _state.update { it.copy(sort = sort) }

    fun toggleLowStock() = _state.update { it.copy(onlyLowStock = !it.onlyLowStock) }
    fun toggleNoImage() = _state.update { it.copy(onlyNoImage = !it.onlyNoImage) }
    fun toggleNoBarcode() = _state.update { it.copy(onlyNoBarcode = !it.onlyNoBarcode) }

    fun clearFilters() {
        _state.value = ManageProductsUiState()
    }

    fun deleteById(id: Int) {
        viewModelScope.launch {
            runCatching { repo.deleteById(id) }
                .onFailure { error ->
                    _message.value = error.message ?: "No se pudo eliminar el producto."
                }
        }
    }

    fun upsert(product: ProductEntity, onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                if (product.id == 0) {
                    repo.addProduct(product)
                } else {
                    repo.updateProduct(product)
                }
            }.onSuccess { id ->
                onDone(id)
            }.onFailure { error ->
                _message.value = error.message ?: "No se pudo guardar el producto."
            }
        }
    }


    suspend fun getSizeStockMap(productId: Int): Map<String, Int> {
        return variantDao.getSizeStocksByProductOnce(productId)
            .mapNotNull { v ->
                val size = v.option1?.trim().orEmpty()
                if (size.isBlank()) null else size to v.quantity.coerceAtLeast(0)
            }
            .toMap()
    }

    fun saveSizeStocks(product: ProductEntity, quantitiesBySize: Map<String, Int>) {
        val normalized = quantitiesBySize
            .mapKeys { it.key.trim() }
            .filterKeys { it.isNotBlank() }
            .mapValues { it.value.coerceAtLeast(0) }
            .filterValues { it > 0 }

        viewModelScope.launch {
            if (normalized.values.sum() > product.quantity) {
                _message.value = "La suma de talles no puede superar el stock total del producto."
                return@launch
            }
            if (normalized.isNotEmpty() && normalized.keys.any { size -> !product.sizes.contains(size) }) {
                _message.value = "Hay talles cargados que no existen en la lista de talles del producto."
                return@launch
            }

            runCatching {
                variantDao.deleteSizeStocksByProduct(product.id)
                if (normalized.isNotEmpty()) {
                    val rows = normalized.map { (size, qty) ->
                        VariantEntity(
                            productId = product.id,
                            sku = null,
                            option1 = size,
                            option2 = null,
                            quantity = qty
                        )
                    }
                    variantDao.insertAll(rows)
                }
            }.onFailure { error ->
                _message.value = error.message ?: "No se pudo guardar el stock por talle."
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

private fun ManageProductsUiState.toFilterParams(): ProductFilterParams = ProductFilterParams(
    query = query,
    parentCategory = parentCategory.ifBlank { null },
    category = category.ifBlank { null },
    color = color.ifBlank { null },
    size = size.ifBlank { null },
    minPrice = minPrice.toDoubleOrNull(),
    maxPrice = maxPrice.toDoubleOrNull(),
    onlyLowStock = onlyLowStock,
    onlyNoImage = onlyNoImage,
    onlyNoBarcode = onlyNoBarcode,
    sort = sort
)
