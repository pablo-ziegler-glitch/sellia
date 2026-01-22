package com.example.selliaapp.viewmodel


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.dao.InvoiceWithItems
import com.example.selliaapp.repository.InvoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * VM para búsqueda de compras por cliente (nombre/teléfono/email/apodo).
 */
@HiltViewModel
class ClientPurchasesViewModel @Inject constructor(
    private val repo: InvoiceRepository
) : ViewModel() {

    private val query = MutableStateFlow("")

    // Cuando la query está vacía, usamos "__no_match__" para evitar traer todo.
    @OptIn(ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<InvoiceWithItems>> =
        query.flatMapLatest { q ->
            val safe = if (q.isBlank()) "__no_match__" else q
            repo.observeInvoicesByCustomerQuery(safe)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setQuery(q: String) { query.value = q }
}
