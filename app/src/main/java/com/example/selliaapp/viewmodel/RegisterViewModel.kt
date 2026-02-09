package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.auth.AuthManager
import com.example.selliaapp.repository.AuthOnboardingRepository
import com.example.selliaapp.repository.TenantDirectoryRepository
import com.example.selliaapp.repository.TenantSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RegisterMode(val label: String) {
    CREATE_STORE("Crear nueva tienda"),
    SELECT_STORE("Elegir tienda existente")
}

data class RegisterUiState(
    val isLoading: Boolean = false,
    val isLoadingTenants: Boolean = false,
    val errorMessage: String? = null,
    val tenants: List<TenantSummary> = emptyList(),
    val selectedTenantId: String? = null,
    val mode: RegisterMode = RegisterMode.SELECT_STORE
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val onboardingRepository: AuthOnboardingRepository,
    private val tenantDirectoryRepository: TenantDirectoryRepository,
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState

    init {
        loadTenants()
    }

    fun register(
        email: String,
        password: String,
        storeName: String,
        selectedTenantId: String?,
        mode: RegisterMode
    ) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Completá email y contraseña") }
            return
        }
        if (mode == RegisterMode.CREATE_STORE && storeName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Indicá el nombre de la tienda") }
            return
        }
        if (mode == RegisterMode.SELECT_STORE && selectedTenantId.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Seleccioná una tienda") }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = when (mode) {
                RegisterMode.CREATE_STORE -> onboardingRepository.registerStore(
                    email = email.trim(),
                    password = password,
                    storeName = storeName.trim()
                )
                RegisterMode.SELECT_STORE -> onboardingRepository.registerViewer(
                    email = email.trim(),
                    password = password,
                    tenantId = selectedTenantId.orEmpty()
                )
            }
            result.onSuccess {
                authManager.refreshSession()
                _uiState.update { state -> state.copy(isLoading = false) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "No se pudo completar el registro"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun updateMode(mode: RegisterMode) {
        _uiState.update {
            it.copy(
                mode = mode,
                errorMessage = null
            )
        }
    }

    fun selectTenant(tenantId: String) {
        _uiState.update { it.copy(selectedTenantId = tenantId, errorMessage = null) }
    }

    private fun loadTenants() {
        _uiState.update { it.copy(isLoadingTenants = true) }
        viewModelScope.launch {
            tenantDirectoryRepository.fetchTenants()
                .onSuccess { tenants ->
                    _uiState.update {
                        it.copy(
                            isLoadingTenants = false,
                            tenants = tenants,
                            selectedTenantId = it.selectedTenantId ?: tenants.firstOrNull()?.id
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingTenants = false,
                            errorMessage = error.message ?: "No se pudieron cargar las tiendas"
                        )
                    }
                }
        }
    }
}
