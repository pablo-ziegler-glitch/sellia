package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.remote.PublicBannerMessage
import com.example.selliaapp.data.remote.StorefrontConfig
import com.example.selliaapp.repository.StorefrontRepository
import com.example.selliaapp.repository.TenantDirectoryRepository
import com.example.selliaapp.repository.TenantSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomerHomeUiState(
    val isLoading: Boolean = false,
    val bannerMessages: List<PublicBannerMessage> = emptyList(),
    val stores: List<TenantSummary> = emptyList(),
    val selectedStore: TenantSummary? = null,
    val selectedStoreConfig: StorefrontConfig? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class CustomerHomeViewModel @Inject constructor(
    private val storefrontRepository: StorefrontRepository,
    private val tenantDirectoryRepository: TenantDirectoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomerHomeUiState())
    val uiState: StateFlow<CustomerHomeUiState> = _uiState

    init {
        loadData()
    }

    fun loadData() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            // Load banner messages and stores in parallel
            val bannersDeferred = storefrontRepository.fetchActiveBannerMessages()
            val storesDeferred = tenantDirectoryRepository.fetchTenants()

            val banners = bannersDeferred.getOrDefault(emptyList())
            val stores = storesDeferred.getOrDefault(emptyList())

            _uiState.update {
                it.copy(
                    isLoading = false,
                    bannerMessages = banners,
                    stores = stores
                )
            }
        }
    }

    fun selectStore(store: TenantSummary?) {
        _uiState.update { it.copy(selectedStore = store, selectedStoreConfig = null) }
        if (store != null) {
            loadStoreConfig(store.id)
        }
    }

    private fun loadStoreConfig(tenantId: String) {
        viewModelScope.launch {
            storefrontRepository.getStorefrontConfig(tenantId)
                .onSuccess { config ->
                    _uiState.update { it.copy(selectedStoreConfig = config) }
                }
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedStore = null, selectedStoreConfig = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
