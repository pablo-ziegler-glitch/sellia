package com.example.selliaapp.viewmodel.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.model.sales.InvoiceDetail
import com.example.selliaapp.data.model.sales.InvoiceSummary
import com.example.selliaapp.repository.InvoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

@HiltViewModel
class InvoiceHistoryViewModel @Inject constructor(
    private val repo: InvoiceRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val detailsById = MutableStateFlow<Map<Long, InvoiceDetail>>(emptyMap())

    private val invoices = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<InvoiceHistoryUiState> = combine(
        invoices,
        query,
        detailsById
    ) { list, search, details ->
        val normalized = search.trim().lowercase(Locale.getDefault())
        val filtered = if (normalized.isBlank()) {
            list
        } else {
            list.filter { invoice -> invoice.matches(normalized, details[invoice.id]) }
        }
        InvoiceHistoryUiState(
            query = search,
            invoices = filtered,
            detailsById = details
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InvoiceHistoryUiState())

    fun onQueryChange(value: String) {
        query.value = value
        if (value.isNotBlank()) {
            preloadDetailsForSearch()
        }
    }

    fun loadDetail(invoiceId: Long) {
        if (detailsById.value.containsKey(invoiceId)) return
        viewModelScope.launch {
            repo.getInvoiceDetail(invoiceId)?.let { detail ->
                detailsById.value = detailsById.value + (invoiceId to detail)
            }
        }
    }

    private fun preloadDetailsForSearch() {
        viewModelScope.launch {
            invoices.value.forEach { invoice ->
                if (!detailsById.value.containsKey(invoice.id)) {
                    repo.getInvoiceDetail(invoice.id)?.let { detail ->
                        detailsById.value = detailsById.value + (invoice.id to detail)
                    }
                }
            }
        }
    }
}

data class InvoiceHistoryUiState(
    val query: String = "",
    val invoices: List<InvoiceSummary> = emptyList(),
    val detailsById: Map<Long, InvoiceDetail> = emptyMap()
)

private fun InvoiceSummary.matches(query: String, detail: InvoiceDetail?): Boolean {
    val dateNumeric = date.format(DateTimeFormatter.ISO_LOCAL_DATE).lowercase(Locale.getDefault())
    val dateHuman = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")).lowercase(Locale.getDefault())
    val productMatch = detail?.items?.any { it.name.lowercase(Locale.getDefault()).contains(query) } == true
    return number.lowercase(Locale.getDefault()).contains(query) ||
        customerName.lowercase(Locale.getDefault()).contains(query) ||
        dateNumeric.contains(query) ||
        dateHuman.contains(query) ||
        productMatch
}
