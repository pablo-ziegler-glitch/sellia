package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.auth.TenantProvider
import com.example.selliaapp.data.remote.BannerMessageLocal
import com.example.selliaapp.data.remote.StorefrontConfig
import com.example.selliaapp.repository.StorefrontRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class StorefrontUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val config: StorefrontConfig = StorefrontConfig(),
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class StorefrontViewModel @Inject constructor(
    private val storefrontRepository: StorefrontRepository,
    private val tenantProvider: TenantProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(StorefrontUiState())
    val uiState: StateFlow<StorefrontUiState> = _uiState

    init {
        loadConfig()
    }

    fun loadConfig() {
        val tenantId = tenantProvider.currentTenantId() ?: return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            storefrontRepository.getStorefrontConfig(tenantId)
                .onSuccess { config ->
                    _uiState.update { it.copy(isLoading = false, config = config) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Error al cargar configuración")
                    }
                }
        }
    }

    fun updateConfig(config: StorefrontConfig) {
        _uiState.update { it.copy(config = config) }
    }

    fun saveConfig() {
        val tenantId = tenantProvider.currentTenantId() ?: return
        val config = _uiState.value.config
        _uiState.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            storefrontRepository.updateStorefrontConfig(tenantId, config)
                .onSuccess {
                    // Sync banner messages to public collection
                    storefrontRepository.syncBannerMessagesToPublic(
                        tenantId = tenantId,
                        storeName = config.storeName,
                        config = config
                    )
                    _uiState.update {
                        it.copy(isSaving = false, successMessage = "Vidriera actualizada")
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isSaving = false, errorMessage = error.message ?: "Error al guardar")
                    }
                }
        }
    }

    fun addBannerMessage(text: String) {
        val current = _uiState.value.config
        val activeCount = current.bannerMessages.count { it.active }
        if (activeCount >= 3) {
            _uiState.update { it.copy(errorMessage = "Máximo 3 mensajes activos") }
            return
        }
        val newMessage = BannerMessageLocal(
            id = UUID.randomUUID().toString(),
            text = text,
            active = true
        )
        _uiState.update {
            it.copy(config = current.copy(bannerMessages = current.bannerMessages + newMessage))
        }
    }

    fun removeBannerMessage(messageId: String) {
        val current = _uiState.value.config
        _uiState.update {
            it.copy(config = current.copy(
                bannerMessages = current.bannerMessages.filter { msg -> msg.id != messageId }
            ))
        }
    }

    fun toggleBannerMessage(messageId: String) {
        val current = _uiState.value.config
        val updated = current.bannerMessages.map { msg ->
            if (msg.id == messageId) msg.copy(active = !msg.active) else msg
        }
        val activeCount = updated.count { it.active }
        if (activeCount > 3) {
            _uiState.update { it.copy(errorMessage = "Máximo 3 mensajes activos") }
            return
        }
        _uiState.update { it.copy(config = current.copy(bannerMessages = updated)) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
