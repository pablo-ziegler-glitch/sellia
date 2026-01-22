package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.repository.IProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManageProductsUiState(
    val query: String = "",
    val onlyLowStock: Boolean = false,
    val onlyNoImage: Boolean = false,
    val onlyNoBarcode: Boolean = false
)

@HiltViewModel
class ManageProductsViewModel @Inject constructor(
    private val repo: IProductRepository
) : ViewModel() {

    // Estado UI (búsqueda + filtros)
    private val _state = MutableStateFlow(ManageProductsUiState())
    val state: StateFlow<ManageProductsUiState> = _state.asStateFlow()

    // Flujo Paginado según búsqueda (filtros simples se aplican en UI por ahora)
    @OptIn(ExperimentalCoroutinesApi::class)
    val productsPaged: Flow<PagingData<ProductEntity>> =
        _state
            .map { it.query.trim() }
            .distinctUntilChanged()
            .flatMapLatest { q ->
                val query = q.takeIf { it.isNotEmpty() } ?: ""
                repo.pagingSearchFlow(query)
            }

    // Listado no paginado (si alguna UI lo necesita)
    val productsAll: Flow<List<ProductEntity>> = repo.observeAll()

    // ---- Acciones sobre estado ----
    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
    }

    fun toggleLowStock() {
        _state.update { it.copy(onlyLowStock = !it.onlyLowStock) }
    }

    fun toggleNoImage() {
        _state.update { it.copy(onlyNoImage = !it.onlyNoImage) }
    }

    fun toggleNoBarcode() {
        _state.update { it.copy(onlyNoBarcode = !it.onlyNoBarcode) }
    }

    // ---- Acciones de datos ----
    fun deleteById(id: Int) {
        viewModelScope.launch {
            repo.deleteById(id)
        }
    }

    fun upsert(product: ProductEntity, onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val id = if (product.id == 0) {
                repo.addProduct(product)
            } else {
                repo.updateProduct(product)
            }
            onDone(id)
        }
    }
}
