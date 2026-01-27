package com.example.selliaapp.viewmodel.sales

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.model.sales.InvoiceDetail
import com.example.selliaapp.repository.InvoiceRepository
import com.example.selliaapp.sync.SyncRepository
import com.example.selliaapp.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SalesInvoiceDetailViewModel @Inject constructor(
    private val repo: InvoiceRepository,
    private val syncRepository: SyncRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val invoiceId: Long =
        savedStateHandle.get<Long>(Routes.SalesInvoiceDetail.ARG_ID) ?: 0L

    private val _state = MutableStateFlow<InvoiceDetail?>(null)
    val state: StateFlow<InvoiceDetail?> = _state

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
            _state.value = repo.getInvoiceDetail(invoiceId)
        }
    }
}
