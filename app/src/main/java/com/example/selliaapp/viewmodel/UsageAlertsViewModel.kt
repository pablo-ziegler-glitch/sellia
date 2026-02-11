package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.model.UsageAlert
import com.example.selliaapp.data.model.usage.UsageLimitOverride
import com.example.selliaapp.domain.usage.UsageMetricKey
import com.example.selliaapp.repository.UsageAlertsRepository
import com.example.selliaapp.repository.UsageLimitsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UsageLimitSummary(
    val metric: String,
    val title: String,
    val currentValue: Double,
    val limitValue: Double,
    val percentage: Int
)

data class UsageAlertsUiState(
    val alerts: List<UsageAlert> = emptyList(),
    val limitSummaries: List<UsageLimitSummary> = emptyList(),
    val currentMetrics: Map<String, Double> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null
) {
    val unreadCount: Int = alerts.count { !it.isRead }
}

@HiltViewModel
class UsageAlertsViewModel @Inject constructor(
    private val repository: UsageAlertsRepository,
    private val limitsRepository: UsageLimitsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        UsageAlertsUiState(
            limitSummaries = buildLimitSummaries(
                alerts = emptyList(),
                overrides = emptyList(),
                currentMetrics = emptyMap()
            )
        )
    )
    val state: StateFlow<UsageAlertsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val alertsDeferred = async { runCatching { repository.fetchAlerts() } }
            val overridesDeferred = async { runCatching { limitsRepository.fetchOverrides() } }
            val metricsDeferred = async { runCatching { repository.fetchCurrentUsageMetrics() } }
            val alertsResult = alertsDeferred.await()
            val overridesResult = overridesDeferred.await()
            val metricsResult = metricsDeferred.await()
            val alerts = alertsResult.getOrDefault(emptyList())
            val overrides = overridesResult.getOrDefault(emptyList())
            val currentMetrics = metricsResult.getOrDefault(emptyMap())
            val summaries = buildLimitSummaries(alerts, overrides, currentMetrics)
            _state.update {
                it.copy(
                    alerts = alerts,
                    limitSummaries = summaries,
                    currentMetrics = currentMetrics,
                    loading = false,
                    error = alertsResult.exceptionOrNull()?.message
                        ?: overridesResult.exceptionOrNull()?.message
                        ?: metricsResult.exceptionOrNull()?.message
                )
            }
        }
    }

    fun updateLimit(metric: String, limitValue: Double) {
        viewModelScope.launch {
            runCatching { limitsRepository.updateOverride(metric, limitValue) }
                .onSuccess {
                    _state.update { state ->
                        val updated = state.limitSummaries.map { summary ->
                            if (summary.metric == metric) {
                                summary.copy(
                                    limitValue = limitValue,
                                    percentage = calculatePercentage(summary.currentValue, limitValue)
                                )
                            } else {
                                summary
                            }
                        }
                        state.copy(limitSummaries = updated)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = error.message ?: "No se pudo actualizar el tope")
                    }
                }
        }
    }

    fun markAlertRead(alertId: String) {
        _state.update { state ->
            state.copy(alerts = state.alerts.map { alert ->
                if (alert.id == alertId) alert.copy(isRead = true) else alert
            })
        }
        viewModelScope.launch {
            runCatching { repository.markAlertRead(alertId) }
        }
    }

    fun markAllRead() {
        val unreadAlerts = _state.value.alerts.filterNot { it.isRead }
        if (unreadAlerts.isEmpty()) return
        _state.update { state ->
            state.copy(alerts = state.alerts.map { it.copy(isRead = true) })
        }
        viewModelScope.launch {
            runCatching {
                repository.markAlertsRead(unreadAlerts.map { it.id })
            }
        }
    }

    private fun buildLimitSummaries(
        alerts: List<UsageAlert>,
        overrides: List<UsageLimitOverride>,
        currentMetrics: Map<String, Double>
    ): List<UsageLimitSummary> {
        val alertByMetric = alerts.groupBy { it.metric }
            .mapValues { (_, group) ->
                group.maxByOrNull { it.currentValue }
            }
            .mapValues { it.value }
        val overridesByMetric = overrides.associateBy { it.metric }
        val defaults = defaultMetrics()
        val extraMetrics = alertByMetric.keys
            .filter { key -> defaults.none { it.key == key } }
            .map { key ->
                UsageMetricDefinition(
                    key = key,
                    label = key.replace('_', ' ').replaceFirstChar { it.uppercase() },
                    defaultLimit = alertByMetric[key]?.limitValue?.takeIf { it > 0 } ?: 100.0
                )
            }
        return (defaults + extraMetrics).map { metric ->
            val alert = alertByMetric[metric.key]
            val override = overridesByMetric[metric.key]
            val current = currentMetrics[metric.key] ?: alert?.currentValue ?: 0.0
            val baseLimit = alert?.limitValue?.takeIf { it > 0 } ?: metric.defaultLimit
            val finalLimit = override?.limitValue?.takeIf { it > 0 } ?: baseLimit
            UsageLimitSummary(
                metric = metric.key,
                title = metric.label,
                currentValue = current,
                limitValue = finalLimit,
                percentage = calculatePercentage(current, finalLimit)
            )
        }
    }

    private fun calculatePercentage(current: Double, limit: Double): Int {
        if (limit <= 0) return 0
        return ((current / limit) * 100).toInt().coerceAtLeast(0)
    }

    private fun defaultMetrics(): List<UsageMetricDefinition> = listOf(
        UsageMetricDefinition(
            key = UsageMetricKey.FIRESTORE_READS,
            label = "Firestore lecturas",
            defaultLimit = 50_000.0
        ),
        UsageMetricDefinition(
            key = UsageMetricKey.FIRESTORE_WRITES,
            label = "Firestore escrituras",
            defaultLimit = 20_000.0
        ),
        UsageMetricDefinition(
            key = UsageMetricKey.FIRESTORE_DELETES,
            label = "Firestore borrados",
            defaultLimit = 20_000.0
        ),
        UsageMetricDefinition(
            key = UsageMetricKey.FIRESTORE_STORAGE_BYTES,
            label = "Firestore almacenamiento (MB)",
            defaultLimit = 1_024.0
        ),
        UsageMetricDefinition(
            key = UsageMetricKey.STORAGE_BYTES,
            label = "Storage archivos (MB)",
            defaultLimit = 5_120.0
        ),
        UsageMetricDefinition(
            key = UsageMetricKey.AUTH_MONTHLY_ACTIVE_USERS,
            label = "Auth usuarios activos",
            defaultLimit = 10_000.0
        ),
        UsageMetricDefinition(
            key = UsageMetricKey.HOSTING_BANDWIDTH_BYTES,
            label = "Hosting transferencia (GB)",
            defaultLimit = 10.0
        )
    )
}

private data class UsageMetricDefinition(
    val key: String,
    val label: String,
    val defaultLimit: Double
)
