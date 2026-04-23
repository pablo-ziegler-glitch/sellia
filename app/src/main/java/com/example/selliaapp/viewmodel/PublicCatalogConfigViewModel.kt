package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.repository.PublicCatalogConfigRepository
import com.example.selliaapp.repository.PublicCatalogSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class PublicCatalogConfigViewModel @Inject constructor(
    private val repository: PublicCatalogConfigRepository
) : ViewModel() {
    val settings = repository.settings
    private val _syncInProgress = MutableStateFlow(false)
    val syncInProgress = _syncInProgress.asStateFlow()
    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage = _syncMessage.asStateFlow()

    init {
        viewModelScope.launch { repository.refreshFromCloud() }
    }

    fun updateSettings(updated: PublicCatalogSettings) {
        viewModelScope.launch {
            repository.updateSettings(updated)
        }
    }

    fun triggerCatalogSync() {
        if (_syncInProgress.value) return
        viewModelScope.launch {
            _syncInProgress.value = true
            runCatching { repository.triggerStoreProductsSync() }
                .onSuccess { syncedCount ->
                    _syncMessage.value = "Sincronización manual OK. Productos sincronizados: $syncedCount."
                }
                .onFailure { error ->
                    _syncMessage.value = error.message ?: "No se pudo forzar la sincronización del catálogo."
                }
            _syncInProgress.value = false
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }
}
