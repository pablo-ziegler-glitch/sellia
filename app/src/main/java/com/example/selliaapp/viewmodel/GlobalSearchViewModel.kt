package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.local.entity.CustomerEntity
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.data.model.Invoice
import com.example.selliaapp.data.model.Provider
import com.example.selliaapp.repository.CustomerRepository
import com.example.selliaapp.repository.InvoiceRepository
import com.example.selliaapp.repository.ProductRepository
import com.example.selliaapp.repository.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val GLOBAL_SEARCH_MIN_LENGTH = 2
private const val GLOBAL_SEARCH_LIMIT = 8

data class GlobalSearchUiState(
    val query: String = "",
    val products: List<ProductEntity> = emptyList(),
    val customers: List<CustomerEntity> = emptyList(),
    val invoices: List<Invoice> = emptyList(),
    val providers: List<Provider> = emptyList()
) {
    val hasQuery: Boolean get() = query.length >= GLOBAL_SEARCH_MIN_LENGTH
    val hasAnyResults: Boolean
        get() = products.isNotEmpty() || customers.isNotEmpty() || invoices.isNotEmpty() || providers.isNotEmpty()
}

@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val customerRepository: CustomerRepository,
    private val invoiceRepository: InvoiceRepository,
    private val providerRepository: ProviderRepository
) : ViewModel() {

    private val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<GlobalSearchUiState> = query
        .flatMapLatest { raw ->
            val trimmed = raw.trim()
            if (trimmed.length < GLOBAL_SEARCH_MIN_LENGTH) {
                flowOf(GlobalSearchUiState(query = raw))
            } else {
                combine(
                    productRepository.search(trimmed).map { it.take(GLOBAL_SEARCH_LIMIT) },
                    customerRepository.search(trimmed).map { it.take(GLOBAL_SEARCH_LIMIT) },
                    invoiceRepository.observeInvoicesWithItems().map { all ->
                        all.map { it.invoice }
                            .filter { invoice ->
                                invoice.customerName?.contains(trimmed, ignoreCase = true) == true ||
                                    invoice.id.toString().contains(trimmed)
                            }
                            .take(GLOBAL_SEARCH_LIMIT)
                    },
                    providerRepository.observeAllModels().map { providers ->
                        providers.filter { provider ->
                            provider.name.contains(trimmed, ignoreCase = true) ||
                                provider.phone?.contains(trimmed, ignoreCase = true) == true
                        }.take(GLOBAL_SEARCH_LIMIT)
                    }
                ) { products, customers, invoices, providers ->
                    GlobalSearchUiState(
                        query = raw,
                        products = products,
                        customers = customers,
                        invoices = invoices,
                        providers = providers
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlobalSearchUiState())

    fun setQuery(value: String) {
        query.value = value.take(80)
    }

    fun clear() {
        query.value = ""
    }
}
