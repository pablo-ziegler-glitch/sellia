package com.example.selliaapp.viewmodel.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.model.sales.DailyProfitSummary
import com.example.selliaapp.data.model.sales.InvoiceProfitSummary
import com.example.selliaapp.repository.InvoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class SalesProfitReportUiState(
    val dailySummaries: List<DailyProfitSummary> = emptyList(),
    val salesList: List<InvoiceProfitSummary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Fecha inicio del rango seleccionado */
    val fromDate: LocalDate = LocalDate.now().withDayOfMonth(1),
    /** Fecha fin del rango seleccionado */
    val toDate: LocalDate = LocalDate.now()
)

@HiltViewModel
class SalesProfitReportViewModel @Inject constructor(
    private val repo: InvoiceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SalesProfitReportUiState())
    val state: StateFlow<SalesProfitReportUiState> = _state

    init {
        // Observar cambios en tiempo real de las ventas
        repo.observeProfitSummaries()
            .onEach { summaries ->
                val from = _state.value.fromDate
                val to = _state.value.toDate
                val filtered = summaries.filter { it.date in from..to }
                _state.update { it.copy(salesList = filtered) }
            }
            .launchIn(viewModelScope)

        loadReport()
    }

    fun setDateRange(from: LocalDate, to: LocalDate) {
        _state.update { it.copy(fromDate = from, toDate = to) }
        loadReport()
    }

    fun selectThisMonth() {
        val today = LocalDate.now()
        setDateRange(today.withDayOfMonth(1), today)
    }

    fun selectLast30Days() {
        val today = LocalDate.now()
        setDateRange(today.minusDays(29), today)
    }

    fun selectToday() {
        val today = LocalDate.now()
        setDateRange(today, today)
    }

    private fun loadReport() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val zona = ZoneId.systemDefault()
                val from = _state.value.fromDate.atStartOfDay(zona).toInstant().toEpochMilli()
                val to = _state.value.toDate.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli() - 1
                val daily = repo.getDailyProfitSummary(from, to)
                val sales = repo.getProfitReport(from, to)
                _state.update { it.copy(dailySummaries = daily, salesList = sales, isLoading = false) }
            }.onFailure { err ->
                _state.update { it.copy(isLoading = false, error = err.message ?: "Error cargando reporte") }
            }
        }
    }
}
