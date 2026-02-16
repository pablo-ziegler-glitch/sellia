package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.model.ReportPoint
import com.example.selliaapp.repository.ReportsRepository
import com.example.selliaapp.repository.StockValuationReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class ReportsFilter { DAY, WEEK, MONTH }


data class ReportsUiState(
    val filter: ReportsFilter = ReportsFilter.DAY,
    val total: Double = 0.0,
    val points: List<Pair<String, Double>> = emptyList(),
    val stockValuation: StockValuationReport? = null,
    val loading: Boolean = false,
    val error: String? = null   // <-- AGREGADO: para poder hacer copy(error = ...)

)

/**
 * ViewModel de reportes:
 * - onFilterChange("Día"/"Semana"/"Mes") -> recalcula rango y series.
 * - load(filter) centraliza la carga (llamado en init y en cambios de filtro).
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reportsRepository: ReportsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReportsUiState())
    val state: StateFlow<ReportsUiState> = _state.asStateFlow()

    // Para gráficos (serie cruda)
    private val _reportData = MutableStateFlow<List<ReportPoint>>(emptyList())
    val reportData: StateFlow<List<ReportPoint>> = _reportData.asStateFlow()

    init {
        load(ReportsFilter.DAY)
    }
    fun onFilterChange(filterText: String) {
        val filter = when (filterText.lowercase()) {
            "día", "dia", "day" -> ReportsFilter.DAY
            "semana", "week"     -> ReportsFilter.WEEK
            "mes", "month"       -> ReportsFilter.MONTH
            else                 -> ReportsFilter.DAY
        }
        load(filter)
    }

    fun onFilterChange(filter: ReportsFilter) = load(filter)

    private fun load(filter: ReportsFilter) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, filter = filter, error = null)
            try {
                val today = LocalDate.now()
                val (from, to) = when (filter) {
                    ReportsFilter.DAY   -> today to today
                    ReportsFilter.WEEK  -> today.minusDays(6) to today
                    ReportsFilter.MONTH -> today.minusDays(29) to today
                }
                val series = reportsRepository.getSalesSeries(from, to, filter)
                val total  = series.sumOf { it.amount }
                val pairs  = series.map { it.label to it.amount }
                val stockValuation = reportsRepository.getStockValuationReport()

                _reportData.value = series
                _state.value = _state.value.copy(
                    loading = false,
                    total = total,
                    points = pairs,
                    stockValuation = stockValuation
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Error al cargar reportes"
                )
            }
        }
    }
}
