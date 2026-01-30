package com.example.selliaapp.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.model.usage.UsageSeriesPoint
import com.example.selliaapp.data.model.usage.UsageServiceSummary
import com.example.selliaapp.repository.UsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class UsageDashboardViewModel @Inject constructor(
    private val usageRepository: UsageRepository
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val total: Double = 0.0,
        val rangeLabel: String = "",
        val series: List<UsageSeriesPoint> = emptyList(),
        val services: List<UsageServiceSummary> = emptyList(),
        val errorMessage: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val to = LocalDate.now()
            val from = to.minusDays(DEFAULT_RANGE_DAYS)
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val snapshot = usageRepository.getUsageDashboard(from = from, to = to)
                _state.value = UiState(
                    isLoading = false,
                    total = snapshot.total,
                    rangeLabel = "${snapshot.from} - ${snapshot.to}",
                    series = snapshot.series,
                    services = snapshot.services,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "No se pudo cargar el consumo"
                )
            }
        }
    }

    private companion object {
        const val DEFAULT_RANGE_DAYS = 29L
    }
}
