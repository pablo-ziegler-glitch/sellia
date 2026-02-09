package com.example.selliaapp.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.model.onboarding.AccountRequest
import com.example.selliaapp.data.model.onboarding.AccountRequestStatus
import com.example.selliaapp.repository.AccountRequestsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountRequestsUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val requests: List<AccountRequest> = emptyList()
)

@HiltViewModel
class AccountRequestsViewModel @Inject constructor(
    private val repository: AccountRequestsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AccountRequestsUiState())
    val state: StateFlow<AccountRequestsUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.fetchRequests()
                .onSuccess { requests ->
                    _state.update { it.copy(isLoading = false, requests = requests) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "No se pudieron cargar las solicitudes"
                        )
                    }
                }
        }
    }

    fun updateRequest(
        requestId: String,
        status: AccountRequestStatus,
        enabledModules: Map<String, Boolean>
    ) {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.updateRequest(requestId, status, enabledModules)
                .onSuccess {
                    repository.fetchRequests()
                        .onSuccess { requests ->
                            _state.update { it.copy(isLoading = false, requests = requests) }
                        }
                        .onFailure { error ->
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = error.message ?: "No se pudieron actualizar los datos"
                                )
                            }
                        }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "No se pudo actualizar la solicitud"
                        )
                    }
                }
        }
    }
}
