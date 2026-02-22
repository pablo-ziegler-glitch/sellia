package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.repository.TenantManagementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TenantManagementUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class TenantManagementViewModel @Inject constructor(
    private val repository: TenantManagementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TenantManagementUiState())
    val uiState: StateFlow<TenantManagementUiState> = _uiState

    fun clearFeedback() {
        _uiState.update { it.copy(message = null, error = null) }
    }

    fun requestDeactivation() {
        _uiState.update { it.copy(isLoading = true, message = null, error = null) }
        viewModelScope.launch {
            repository.requestTenantDeactivation()
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, message = "Tienda dada de baja lógica correctamente") }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "No se pudo dar de baja la tienda") }
                }
        }
    }

    fun requestReactivation() {
        _uiState.update { it.copy(isLoading = true, message = null, error = null) }
        viewModelScope.launch {
            repository.requestTenantReactivation()
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, message = "Solicitud de reactivación enviada") }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "No se pudo solicitar reactivación") }
                }
        }
    }

    fun deleteTenant(confirmTenantId: String, confirmPhrase: String) {
        _uiState.update { it.copy(isLoading = true, message = null, error = null) }
        viewModelScope.launch {
            repository.deleteTenantWithDoubleCheck(confirmTenantId, confirmPhrase)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, message = "Tienda eliminada") }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "No se pudo eliminar la tienda") }
                }
        }
    }
}
