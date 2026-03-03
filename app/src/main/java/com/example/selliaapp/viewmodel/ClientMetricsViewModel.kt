package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.repository.CustomerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ClientMetricsUiState(
    val day: Int = 0,
    val week: Int = 0,
    val month: Int = 0,
    val year: Int = 0,
    val isLoading: Boolean = false
)

@HiltViewModel
class ClientMetricsViewModel @Inject constructor(
    private val repo: CustomerRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ClientMetricsUiState(isLoading = true))
    val state: StateFlow<ClientMetricsUiState> = _state

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true) }
        runCatching {
            val day = repo.countToday()
            val week = repo.countThisWeek()
            val month = repo.countThisMonth()
            val year = repo.countThisYear()
            ClientMetricsUiState(day, week, month, year, isLoading = false)
        }.onSuccess { next ->
            _state.value = next
        }.onFailure {
            _state.update { it.copy(isLoading = false) }
        }
    }
}
