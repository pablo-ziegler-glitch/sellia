package com.floki.app.ui.viewmodel


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floki.app.repository.OffRepository
import com.floki.app.viewmodel.PrefillData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel liviano para consultar Open Food Facts y producir un PrefillData.
 * No toca tu ProductViewModel. Solo concentra el fetch y estado UI (loading/error/data).
 *
 * [NUEVO] Ajustado a DTO camelCase: productName, imageUrl.
 */
@HiltViewModel
class OffLookupViewModel @Inject constructor(
    private val offRepository: OffRepository
) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Success(val data: PrefillData) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    /**
     * Busca sugerencias en OFF por código. Si no existe, deja Success con solo el barcode.
     */
    fun fetch(barcode: String) {
        if (barcode.isBlank()) return
        _state.value = UiState.Loading
        viewModelScope.launch {
            try {
                val off = offRepository.getProductSuggestion(barcode)
                val prefill = if (off != null) {
                    PrefillData(
                        barcode = barcode,
                        name = off.productName ?: "",
                        brand = off.brands?.split(",")?.firstOrNull()?.trim() ?: "",
                        imageUrl = off.imageUrl
                    )
                } else {
                    PrefillData(barcode = barcode)
                }
                _state.value = UiState.Success(prefill)
            } catch (e: Exception) {
                _state.value = UiState.Error("No se pudo consultar Open Food Facts: ${e.message ?: "Error desconocido"}")
            }
        }
    }
}