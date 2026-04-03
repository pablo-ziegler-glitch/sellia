package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.repository.PublicCatalogProduct
import com.example.selliaapp.repository.ViewerStoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoreDetailUiState(
    val isLoadingCatalog: Boolean = false,
    val catalog: List<PublicCatalogProduct> = emptyList(),
    val catalogErrorMessage: String? = null
)

@HiltViewModel
class StoreDetailViewModel @Inject constructor(
    private val viewerStoreRepository: ViewerStoreRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoreDetailUiState())
    val uiState: StateFlow<StoreDetailUiState> = _uiState

    private var currentTenantId: String? = null

    fun loadCatalog(tenantId: String, force: Boolean = false) {
        if (tenantId.isBlank()) return
        if (!force && currentTenantId == tenantId && (_uiState.value.catalog.isNotEmpty() || _uiState.value.isLoadingCatalog)) {
            return
        }

        currentTenantId = tenantId
        _uiState.update {
            it.copy(
                isLoadingCatalog = true,
                catalogErrorMessage = null
            )
        }

        viewModelScope.launch {
            viewerStoreRepository.fetchPublicCatalog(tenantId)
                .onSuccess { products ->
                    _uiState.update {
                        it.copy(
                            isLoadingCatalog = false,
                            catalog = products,
                            catalogErrorMessage = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingCatalog = false,
                            catalog = emptyList(),
                            catalogErrorMessage = error.message ?: "No se pudo cargar el catálogo"
                        )
                    }
                }
        }
    }

    fun retryCatalog() {
        val tenantId = currentTenantId ?: return
        loadCatalog(tenantId = tenantId, force = true)
    }
}
