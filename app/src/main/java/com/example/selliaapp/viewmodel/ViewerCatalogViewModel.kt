package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.repository.PublicCatalogProduct
import com.example.selliaapp.repository.TenantDirectoryRepository
import com.example.selliaapp.repository.TenantSummary
import com.example.selliaapp.repository.ViewerStoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ViewerCatalogUiState(
    val isLoading: Boolean = false,
    val isLoadingStores: Boolean = false,
    val isFollowingStore: Boolean = false,
    val errorMessage: String? = null,
    val availableStores: List<TenantSummary> = emptyList(),
    val followedStores: List<TenantSummary> = emptyList(),
    val selectedStoreId: String? = null,
    val products: List<PublicCatalogProduct> = emptyList()
) {
    val hasStoresToFollow: Boolean get() = availableStores.isNotEmpty()
    val hasFollowedStores: Boolean get() = followedStores.isNotEmpty()
}

@HiltViewModel
class ViewerCatalogViewModel @Inject constructor(
    private val tenantDirectoryRepository: TenantDirectoryRepository,
    private val viewerStoreRepository: ViewerStoreRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewerCatalogUiState())
    val uiState: StateFlow<ViewerCatalogUiState> = _uiState

    init {
        refresh()
    }

    fun refresh() {
        loadAvailableStores()
        loadViewerSelection()
    }

    fun followSelectedStore(storeId: String) {
        val store = _uiState.value.availableStores.firstOrNull { it.id == storeId }
            ?: return
        _uiState.update { it.copy(isFollowingStore = true, errorMessage = null) }
        viewModelScope.launch {
            viewerStoreRepository.followStore(store)
                .onSuccess { loadViewerSelection() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isFollowingStore = false,
                            errorMessage = error.message ?: "No se pudo adherir a la tienda"
                        )
                    }
                }
        }
    }

    fun selectStore(storeId: String) {
        _uiState.update { it.copy(selectedStoreId = storeId, errorMessage = null) }
        viewModelScope.launch {
            viewerStoreRepository.selectStore(storeId)
                .onSuccess { loadPublicCatalog(storeId) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "No se pudo seleccionar la tienda")
                    }
                }
        }
    }

    private fun loadAvailableStores() {
        _uiState.update { it.copy(isLoadingStores = true) }
        viewModelScope.launch {
            tenantDirectoryRepository.fetchTenants()
                .onSuccess { stores ->
                    _uiState.update {
                        it.copy(
                            isLoadingStores = false,
                            availableStores = stores
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingStores = false,
                            errorMessage = error.message ?: "No se pudieron cargar las tiendas"
                        )
                    }
                }
        }
    }

    private fun loadViewerSelection() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            viewerStoreRepository.fetchViewerStoreSelection()
                .onSuccess { selection ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isFollowingStore = false,
                            followedStores = selection.followedStores,
                            selectedStoreId = selection.selectedStoreId
                        )
                    }
                    if (selection.selectedStoreId != null) {
                        loadPublicCatalog(selection.selectedStoreId)
                    } else {
                        _uiState.update { it.copy(products = emptyList()) }
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isFollowingStore = false,
                            errorMessage = error.message ?: "No se pudo cargar la tienda seleccionada"
                        )
                    }
                }
        }
    }

    private fun loadPublicCatalog(storeId: String) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            viewerStoreRepository.fetchPublicCatalog(storeId)
                .onSuccess { products ->
                    _uiState.update { it.copy(isLoading = false, products = products) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            products = emptyList(),
                            errorMessage = error.message ?: "No se pudo cargar el catálogo"
                        )
                    }
                }
        }
    }
}
