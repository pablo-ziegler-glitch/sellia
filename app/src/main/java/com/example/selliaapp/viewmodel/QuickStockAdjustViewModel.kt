package com.example.selliaapp.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.model.stock.StockAdjustmentReason
import com.example.selliaapp.data.model.stock.StockMovementReasons
import com.example.selliaapp.data.model.stock.StockMovementWithProduct
import com.example.selliaapp.repository.IProductRepository
import com.example.selliaapp.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private val movementFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault())

data class QuickAdjustUiState(
    val loading: Boolean = true,
    val product: ProductEntity? = null,
    val deltaText: String = "",
    val selectedReason: StockAdjustmentReason = StockAdjustmentReason.INVENTORY,
    val note: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val recentMovements: List<StockMovementWithProduct> = emptyList()
) {
    val formattedMovements: List<String>
        get() = recentMovements.map {
            val direction = if (it.delta >= 0) "+${it.delta}" else it.delta.toString()
            val reason = StockMovementReasons.humanReadable(it.reason)
            val ts = movementFormatter.format(it.ts)
            val notePart = it.note?.takeIf { n -> n.isNotBlank() }?.let { n -> " • $n" } ?: ""
            "$ts • $direction • $reason$notePart"
        }
}

@HiltViewModel
class QuickStockAdjustViewModel @Inject constructor(
    private val repo: IProductRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val productId: Int = checkNotNull(
        savedStateHandle.get<Int>(Routes.QuickAdjustStock.ARG_PRODUCT_ID)
    )

    private val _state = MutableStateFlow(QuickAdjustUiState())
    val state: StateFlow<QuickAdjustUiState> = _state.asStateFlow()

    init {
        loadProduct()
        observeMovements()
    }

    private fun loadProduct() {
        viewModelScope.launch {
            val product = repo.getById(productId)
            val deficit = product?.let { p ->
                val min = p.minStock ?: 0
                val current = p.quantity
                val missing = (min - current).coerceAtLeast(0)
                if (missing > 0) missing.toString() else ""
            } ?: ""
            _state.update {
                it.copy(
                    loading = false,
                    product = product,
                    deltaText = deficit,
                    error = if (product == null) "Producto no encontrado" else null
                )
            }
        }
    }

    private fun observeMovements() {
        viewModelScope.launch {
            repo.observeStockMovements(productId, limit = 5).collect { list ->
                _state.update { it.copy(recentMovements = list) }
            }
        }
    }

    fun onDeltaChange(value: String) {
        _state.update { it.copy(deltaText = value, error = null, success = false) }
    }

    fun onReasonSelected(reason: StockAdjustmentReason) {
        _state.update { it.copy(selectedReason = reason, success = false) }
    }

    fun onNoteChange(note: String) {
        _state.update { it.copy(note = note, success = false) }
    }

    fun submit() {
        val product = state.value.product ?: return
        val delta = state.value.deltaText.toIntOrNull()
        if (delta == null || delta == 0) {
            _state.update { it.copy(error = "Ingresá una cantidad distinta de cero") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            val note = state.value.note.trim().takeIf { it.isNotEmpty() }
            val success = repo.adjustStock(
                productId = product.id,
                delta = delta,
                reason = state.value.selectedReason.code,
                note = note
            )
            _state.update {
                it.copy(
                    isSaving = false,
                    success = success,
                    error = if (success) null else "No se pudo registrar el ajuste"
                )
            }
        }
    }
}
