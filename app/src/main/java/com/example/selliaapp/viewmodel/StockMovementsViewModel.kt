package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.model.stock.StockMovementReasons
import com.example.selliaapp.data.model.stock.StockMovementWithProduct
import com.example.selliaapp.repository.IProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import javax.inject.Inject

data class StockMovementsUiState(
    val loading: Boolean = true,
    val movements: List<StockMovementWithProduct> = emptyList(),
    val allReasons: List<String> = emptyList(),
    val selectedReason: String? = null
)

@HiltViewModel
class StockMovementsViewModel @Inject constructor(
    private val productRepository: IProductRepository
) : ViewModel() {

    private val selectedReason = MutableStateFlow<String?>(null)

    private val baseFlow = productRepository.observeRecentStockMovements(limit = 100)

    val state: StateFlow<StockMovementsUiState> = combine(baseFlow, selectedReason) { movements, reason ->
        val reasons = movements.map { it.reason }.distinct()
        val filtered = reason?.let { r -> movements.filter { it.reason == r } } ?: movements
        StockMovementsUiState(
            loading = false,
            movements = filtered,
            allReasons = reasons,
            selectedReason = reason
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StockMovementsUiState())

    fun selectReason(reason: String?) {
        selectedReason.value = reason
    }

    fun readableReason(code: String): String = StockMovementReasons.humanReadable(code)
}
