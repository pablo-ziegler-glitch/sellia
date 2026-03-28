package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.dao.InvoiceDao
import com.example.selliaapp.data.local.entity.CustomerEntity
import com.example.selliaapp.repository.CustomerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

enum class CustomerFrequencyFilter {
    ALL,
    TOP_CLIENTS,
    NO_RECENT_PURCHASES
}

data class CustomerPurchaseInsight(
    val customer: CustomerEntity,
    val purchasesCount: Int = 0,
    val totalSpent: Double = 0.0,
    val lastPurchaseMillis: Long? = null,
    val mostPurchasedProduct: String? = null,
    val mostPurchasedUnits: Int = 0
)

@HiltViewModel
class ManageCustomersViewModel @Inject constructor(
    private val repo: CustomerRepository,
    private val invoiceDao: InvoiceDao
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val frequencyFilter = MutableStateFlow(CustomerFrequencyFilter.ALL)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val filteredCustomers = query.flatMapLatest { q -> repo.search(q) }

    val customerInsights: StateFlow<List<CustomerPurchaseInsight>> =
        combine(
            filteredCustomers,
            invoiceDao.observeInvoicesWithItems(),
            frequencyFilter
        ) { customers, invoicesWithItems, filter ->
            val now = System.currentTimeMillis()
            val staleThreshold = now - TimeUnit.DAYS.toMillis(60)

            val insights = customers.map { customer ->
                val customerInvoices = invoicesWithItems.filter { invoice ->
                    invoice.invoice.customerId == customer.id
                }
                val productBreakdown = customerInvoices
                    .flatMap { it.items }
                    .groupBy { it.productName }
                    .mapValues { (_, items) -> items.sumOf { it.quantity } }
                val topProductEntry = productBreakdown.maxByOrNull { it.value }

                CustomerPurchaseInsight(
                    customer = customer,
                    purchasesCount = customerInvoices.size,
                    totalSpent = customerInvoices.sumOf { it.invoice.total },
                    lastPurchaseMillis = customerInvoices.maxOfOrNull { it.invoice.dateMillis },
                    mostPurchasedProduct = topProductEntry?.key,
                    mostPurchasedUnits = topProductEntry?.value ?: 0
                )
            }

            when (filter) {
                CustomerFrequencyFilter.ALL -> insights
                CustomerFrequencyFilter.TOP_CLIENTS -> insights
                    .filter { it.purchasesCount > 0 }
                    .sortedByDescending { it.purchasesCount }
                    .take(20)
                CustomerFrequencyFilter.NO_RECENT_PURCHASES -> insights.filter { insight ->
                    insight.lastPurchaseMillis == null || insight.lastPurchaseMillis < staleThreshold
                }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    fun setQuery(q: String) { query.value = q }

    fun setFrequencyFilter(filter: CustomerFrequencyFilter) {
        frequencyFilter.value = filter
    }

    fun save(customer: CustomerEntity, onDone: () -> Unit) = viewModelScope.launch {
        runCatching { repo.upsert(customer) }.onSuccess { onDone() }
    }

    fun delete(customer: CustomerEntity) = viewModelScope.launch {
        runCatching { repo.delete(customer) }
    }
}
