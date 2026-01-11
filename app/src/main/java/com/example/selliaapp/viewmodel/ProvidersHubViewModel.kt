package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.model.dashboard.LowStockProduct
import com.example.selliaapp.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProvidersHubState(
    val lowStockAlerts: List<LowStockProduct> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class ProvidersHubViewModel @Inject constructor(
    private val productRepository: ProductRepository
) : ViewModel() {

    companion object {
        private const val ALERT_LIMIT = 5
    }

    private val _state = MutableStateFlow(ProvidersHubState())
    val state: StateFlow<ProvidersHubState> = _state

    init {
        observeLowStock()
    }

    private fun observeLowStock() {
        viewModelScope.launch {
            productRepository.lowStockAlerts(ALERT_LIMIT)
                .catch { error ->
                    _state.update {
                        it.copy(
                            errorMessage = error.localizedMessage
                                ?: "No fue posible cargar alertas de stock"
                        )
                    }
                }
                .collectLatest { alerts ->
                    _state.update { it.copy(lowStockAlerts = alerts, errorMessage = null) }
                }
        }
    }
}
