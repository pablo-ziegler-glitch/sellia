package com.example.selliaapp.viewmodel.sales

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.model.sales.InvoiceDetail
import com.example.selliaapp.repository.InvoiceRepository
import com.example.selliaapp.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val invoiceId: Long =
        savedStateHandle.get<Long>(Routes.SalesInvoiceDetail.ARG_ID) ?: 0L

    private val _state = MutableStateFlow(SalesInvoiceDetailUiState())
    val state: StateFlow<SalesInvoiceDetailUiState> = _state

    init {
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
