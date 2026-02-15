package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.auth.AuthErrorMapper
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
    FINAL_CUSTOMER("Cliente final"),
    STORE_OWNER("Dueño tienda")
}

data class RegisterUiState(
    val isLoading: Boolean = false,
    val isLoadingTenants: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val tenants: List<TenantSummary> = emptyList(),
    val selectedTenantId: String? = null,
    val mode: RegisterMode = RegisterMode.FINAL_CUSTOMER,
    val requiresTenantSelectionOnboarding: Boolean = false
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

    fun setRequiresTenantSelectionOnboarding(required: Boolean) {
        _uiState.update {
            it.copy(
                requiresTenantSelectionOnboarding = required,
                mode = if (required) RegisterMode.FINAL_CUSTOMER else it.mode,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun completeTenantSelectionOnboarding(tenantId: String?, tenantName: String?) {
        if (tenantId.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Seleccioná una tienda") }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            authManager.completePublicCustomerOnboarding(tenantId, tenantName)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, requiresTenantSelectionOnboarding = false) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = AuthErrorMapper.toUserMessage(error, "No se pudo completar el onboarding")
                        )
                    }
                }
        }
    }

    fun register(
        email: String,
        password: String,
        storeName: String,
        storeAddress: String,
        storePhone: String,
        skuPrefix: String,
        selectedTenantId: String?,
        selectedTenantName: String?,
        customerName: String,
        customerPhone: String?,
        mode: RegisterMode
    ) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Completá email y contraseña") }
            return
        }
        if (mode == RegisterMode.STORE_OWNER && storeName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Indicá el nombre de la tienda") }
            return
        }
        if (mode == RegisterMode.STORE_OWNER && skuPrefix.isNotBlank()) {
            val normalizedPrefix = skuPrefix.trim().uppercase().replace("[^A-Z0-9]".toRegex(), "")
            if (normalizedPrefix.length < 3) {
                _uiState.update { it.copy(errorMessage = "El prefijo SKU debe tener al menos 3 caracteres alfanuméricos") }
                return
            }
        }
        if (mode == RegisterMode.STORE_OWNER && storeAddress.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Indicá la dirección comercial") }
            return
        }
        if (mode == RegisterMode.STORE_OWNER && storePhone.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Indicá el teléfono comercial") }
            return
        }
        if (mode == RegisterMode.FINAL_CUSTOMER && customerName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Ingresá tu nombre") }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            val result = when (mode) {
                RegisterMode.STORE_OWNER -> onboardingRepository.registerStore(
                    email = email.trim(),
                    password = password,
                    storeName = storeName.trim(),
                    storeAddress = storeAddress.trim(),
                    storePhone = storePhone.trim(),
                    skuPrefix = skuPrefix.trim().ifBlank { null }
                )
                RegisterMode.FINAL_CUSTOMER -> onboardingRepository.registerViewer(
                    email = email.trim(),
                    password = password,
                    tenantId = selectedTenantId,
                    tenantName = selectedTenantName,
                    customerName = customerName.trim(),
                    customerPhone = customerPhone?.trim()
                )
            }
            result.onSuccess {
                authManager.signOut()
                val successMessage = if (mode == RegisterMode.STORE_OWNER) {
                    "Cuenta creada. Verificá tu email para continuar; además un administrador debe habilitar tu tienda."
                } else {
                    if (selectedTenantId.isNullOrBlank()) "Cuenta creada sin tienda asociada. Podrás adherirte desde tu inicio." else "Cuenta creada. Te enviamos un email de verificación. Confirmalo antes de ingresar."
                }
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        successMessage = successMessage
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = AuthErrorMapper.toUserMessage(error, "No se pudo completar el registro")
                    )
                }
            }
        }
    }

    fun registerWithGoogle(idToken: String, tenantId: String?, tenantName: String?) {
        if (tenantId.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Seleccioná una tienda") }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            val result = onboardingRepository.registerViewerWithGoogle(
                idToken = idToken,
                tenantId = tenantId,
                tenantName = tenantName.orEmpty()
            )
            result.onSuccess {
                authManager.refreshSession()
                _uiState.update { state -> state.copy(isLoading = false) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = AuthErrorMapper.toUserMessage(error, "No se pudo completar el registro")
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun updateMode(mode: RegisterMode) {
        _uiState.update { it.copy(mode = mode, errorMessage = null, successMessage = null) }
    }

    fun selectTenant(tenantId: String) {
        _uiState.update { it.copy(selectedTenantId = tenantId, errorMessage = null, successMessage = null) }
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
