package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.dao.InvoiceWithItems
import com.example.selliaapp.data.model.Invoice
import com.example.selliaapp.data.model.InvoiceItem
import com.example.selliaapp.repository.InvoiceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel de facturas (compatibilidad).
 */
class InvoiceViewModel(
    private val repository: InvoiceRepository
) : ViewModel() {

    val invoices: StateFlow<List<InvoiceWithItems>> =
        repository.observeInvoicesWithItems()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    fun addInvoice(invoice: Invoice, items: List<InvoiceItem>) {
        viewModelScope.launch {
            runCatching { repository.addInvoiceAndAdjustStock(invoice, items) }
        }
    }
}