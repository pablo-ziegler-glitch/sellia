package com.example.selliaapp.viewmodel


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.model.dashboard.DailySalesPoint
import com.example.selliaapp.data.model.dashboard.LowStockProduct
import com.example.selliaapp.data.dao.InvoiceWithItems
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.repository.CashRepository
import com.example.selliaapp.repository.CashSessionSummary
import com.example.selliaapp.repository.InvoiceRepository
import com.example.selliaapp.repository.ProductRepository
import com.example.selliaapp.repository.ProviderInvoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.Duration
import javax.inject.Inject

enum class HomeKpi(val label: String) {
    DAILY_SALES("Ventas diarias"),
    MARGIN("Margen"),
    AVG_TICKET("Ticket medio")
}

data class HomeUiState(
    val monthTotal: Double = 0.0,
    val weekSales: List<DailySalesPoint> = emptyList(),
    val isLoading: Boolean = false,
    val lowStockAlerts: List<LowStockProduct> = emptyList(),
    val overdueProviderInvoices: Int = 0,
    val selectedKpis: List<HomeKpi> = listOf(
        HomeKpi.DAILY_SALES,
        HomeKpi.MARGIN,
        HomeKpi.AVG_TICKET
    ),
    val dailySales: Double = 0.0,
    val dailyMargin: Double = 0.0,
    val averageTicket: Double = 0.0,
    val errorMessage: String? = null,
    val cashSummary: CashSessionSummary? = null
)

val HomeUiState.hasOpenCashSession: Boolean
    get() = cashSummary != null


@HiltViewModel
class HomeViewModel @Inject constructor(
    private val invoiceRepo: InvoiceRepository,
    private val productRepo: ProductRepository,
    private val providerInvoiceRepo: ProviderInvoiceRepository,
    private val cashRepository: CashRepository
) : ViewModel() {

    companion object {
        private const val ALERT_LIMIT = 5
        private const val DIAS_SEMANA = 7
        private const val OVERDUE_DAYS = 30L
    }

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    init {
        observeLowStock()
        observePendingProviderInvoices()
        observeKpiMetrics()
        observeCashSession()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }

            runCatching {
                val totalMes = async { invoiceRepo.sumThisMonth() }
                val serieSemana = async { invoiceRepo.salesLastDays(DIAS_SEMANA) }
                totalMes.await() to serieSemana.await()
            }.onSuccess { (totalMes, serieSemana) ->
                _state.update {
                    it.copy(
                        monthTotal = totalMes,
                        weekSales = serieSemana,
                        isLoading = false
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage ?: "Error inesperado al cargar métricas"
                    )
                }
            }
        }
    }

    private fun observeLowStock(limit: Int = ALERT_LIMIT) {
        viewModelScope.launch {
            productRepo.lowStockAlerts(limit)
                .catch { error ->
                    _state.update {
                        it.copy(errorMessage = error.localizedMessage ?: "No fue posible cargar alertas de stock")
                    }
                }
                .collectLatest { alerts ->
                    _state.update { it.copy(lowStockAlerts = alerts) }
                }
        }
    }

    fun setSelectedKpis(kpis: List<HomeKpi>) {
        _state.update { it.copy(selectedKpis = kpis.distinct()) }
    }

    private fun observePendingProviderInvoices() {
        viewModelScope.launch {
            providerInvoiceRepo.observePending()
                .catch { error ->
                    _state.update {
                        it.copy(errorMessage = error.localizedMessage ?: "No fue posible cargar facturas pendientes")
                    }
                }
                .collectLatest { invoices ->
                    val overdueLimit = System.currentTimeMillis() - Duration.ofDays(OVERDUE_DAYS).toMillis()
                    val overdueCount = invoices.count { it.invoice.issueDateMillis < overdueLimit }
                    _state.update { it.copy(overdueProviderInvoices = overdueCount) }
                }
        }
    }

    private fun observeKpiMetrics() {
        viewModelScope.launch {
            combine(
                invoiceRepo.observeInvoicesWithItems(),
                productRepo.observeAll()
            ) { invoices, products ->
                computeKpiSnapshot(invoices, products)
            }
                .catch { error ->
                    _state.update {
                        it.copy(errorMessage = error.localizedMessage ?: "No fue posible cargar KPIs")
                    }
                }
                .collectLatest { snapshot ->
                    _state.update {
                        it.copy(
                            dailySales = snapshot.dailySales,
                            dailyMargin = snapshot.dailyMargin,
                            averageTicket = snapshot.averageTicket
                        )
                    }
                }
        }
    }

    private fun observeCashSession() {
        viewModelScope.launch {
            cashRepository.observeOpenSessionSummary()
                .catch { error ->
                    _state.update {
                        it.copy(errorMessage = error.localizedMessage ?: "No fue posible cargar la caja")
                    }
                }
                .collectLatest { summary ->
                    _state.update { it.copy(cashSummary = summary) }
                }
        }
    }

    private fun computeKpiSnapshot(
        invoices: List<InvoiceWithItems>,
        products: List<ProductEntity>
    ): KpiSnapshot {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val todayInvoices = invoices.filter { it.invoice.dateMillis in startMillis until endMillis }

        val dailySales = todayInvoices.sumOf { it.invoice.total }
        val averageTicket = if (todayInvoices.isEmpty()) {
            0.0
        } else {
            dailySales / todayInvoices.size
        }

        val productMap = products.associateBy { it.id }
        val dailyMargin = todayInvoices.sumOf { invoice ->
            invoice.items.sumOf { item ->
                val product = productMap[item.productId]
                val cost = product?.purchasePrice ?: product?.price ?: 0.0
                (item.unitPrice - cost) * item.quantity
            }
        }

        return KpiSnapshot(
            dailySales = dailySales,
            dailyMargin = dailyMargin,
            averageTicket = averageTicket
        )
    }

    private data class KpiSnapshot(
        val dailySales: Double,
        val dailyMargin: Double,
        val averageTicket: Double
    )
}
