package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.model.UsageAlert
import com.example.selliaapp.repository.UsageAlertsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UsageAlertsUiState(
    val alerts: List<UsageAlert> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
) {
    val unreadCount: Int = alerts.count { !it.isRead }
}

@HiltViewModel
class UsageAlertsViewModel @Inject constructor(
    private val repository: UsageAlertsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(UsageAlertsUiState())
    val state: StateFlow<UsageAlertsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repository.fetchAlerts() }
                .onSuccess { alerts ->
                    _state.update {
                        it.copy(alerts = alerts, loading = false)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = error.message ?: "No se pudieron cargar las alertas"
                        )
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
}
