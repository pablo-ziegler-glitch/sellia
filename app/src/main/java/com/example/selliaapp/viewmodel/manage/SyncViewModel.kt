package com.example.selliaapp.viewmodel.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.repository.CloudServiceConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SyncUiState(
    val cloudEnabled: Boolean = false
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    repository: CloudServiceConfigRepository
) : ViewModel() {
    val uiState: StateFlow<SyncUiState> = repository.observeConfigs()
        .map { configs -> SyncUiState(cloudEnabled = configs.any { it.cloudEnabled }) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SyncUiState()
        )
}
