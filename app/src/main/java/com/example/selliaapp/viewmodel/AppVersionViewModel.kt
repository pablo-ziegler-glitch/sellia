package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.repository.AppVersionHistoryEntry
import com.example.selliaapp.repository.AppVersionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppVersionUiState(
    val isLoading: Boolean = true,
    val currentVersionLabel: String = "No disponible",
    val history: List<AppVersionHistoryEntry> = emptyList()
)

@HiltViewModel
class AppVersionViewModel @Inject constructor(
    private val repository: AppVersionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AppVersionUiState())
    val state: StateFlow<AppVersionUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        val installed = repository.installedVersion()
        _state.value = _state.value.copy(
            isLoading = true,
            currentVersionLabel = "${installed.versionName} (${installed.versionCode})"
        )

        viewModelScope.launch {
            val history = repository.getVersionHistory()
                .getOrDefault(emptyList())
            _state.value = _state.value.copy(
                isLoading = false,
                history = history
            )
        }
    }
}
