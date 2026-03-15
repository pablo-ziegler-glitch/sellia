package com.example.selliaapp.viewmodel.sales

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.model.sales.InvoiceDetail
import com.example.selliaapp.domain.security.Permission
import com.example.selliaapp.repository.AccessControlRepository
import com.example.selliaapp.repository.InvoiceRepository
import com.example.selliaapp.sync.SyncRepository
import com.example.selliaapp.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SalesInvoiceDetailUiState(
    val detail: InvoiceDetail? = null,
    val isCancelling: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SalesInvoiceDetailViewModel @Inject constructor(
    private val repo: InvoiceRepository,
    private val syncRepository: SyncRepository,
    private val savedStateHandle: SavedStateHandle,
    accessControlRepository: AccessControlRepository
) : ViewModel() {

    /** Verdadero si el usuario tiene permiso para ver el desglose de ganancia. */
    val canViewProfitBreakdown: StateFlow<Boolean> =
        accessControlRepository.observeAccessState()
            .map { it.permissions.contains(Permission.VIEW_PROFIT_BREAKDOWN) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val invoiceId: Long =
        savedStateHandle.get<Long>(Routes.SalesInvoiceDetail.ARG_ID) ?: 0L

    private val _state = MutableStateFlow(SalesInvoiceDetailUiState())
    val state: StateFlow<SalesInvoiceDetailUiState> = _state

    private val _isRetrying = MutableStateFlow(false)
    val isRetrying: StateFlow<Boolean> = _isRetrying

    init {
        refresh()
    }

    fun retrySync() {
        viewModelScope.launch {
            _isRetrying.value = true
            runCatching { syncRepository.pushPending() }
            _isRetrying.value = false
            refresh()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(detail = repo.getInvoiceDetail(invoiceId), error = null) }
        }
    }

    fun cancelInvoice(reason: String) {
        viewModelScope.launch {
            _state.update { it.copy(isCancelling = true, error = null) }
            runCatching {
                repo.cancelInvoice(invoiceId, reason)
            }.onFailure { err ->
                _state.update { it.copy(isCancelling = false, error = err.message ?: "No se pudo anular la venta") }
            }.onSuccess {
                val detail = repo.getInvoiceDetail(invoiceId)
                _state.update { it.copy(isCancelling = false, detail = detail, error = null) }
            }
        }
    }
}
