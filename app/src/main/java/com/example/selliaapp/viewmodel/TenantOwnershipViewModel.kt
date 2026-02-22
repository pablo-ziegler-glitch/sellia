package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.auth.AuthErrorMapper
import com.example.selliaapp.repository.TenantOwnershipRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TenantOwnershipViewModel @Inject constructor(
    private val repository: TenantOwnershipRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TenantOwnershipUiState())
    val state: StateFlow<TenantOwnershipUiState> = _state.asStateFlow()

    fun clearMessage() {
        _state.update { it.copy(message = null, error = null) }
    }

    fun associateOwner(targetEmail: String) {
        executeAction("Asociar dueño") {
            repository.associateOwner(targetEmail)
        }
    }

    fun transferPrimaryOwner(targetEmail: String, keepPreviousOwnerAccess: Boolean) {
        executeAction("Transferir dueño") {
            repository.transferPrimaryOwner(targetEmail, keepPreviousOwnerAccess)
        }
    }

    fun delegateStore(targetEmail: String) {
        executeAction("Delegar tienda") {
            repository.delegateStore(targetEmail)
        }
    }

    private fun executeAction(
        actionLabel: String,
        action: suspend () -> Result<Unit>
    ) {
        if (_state.value.isLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, message = null, error = null) }
            action()
                .onSuccess {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            message = "$actionLabel completado correctamente"
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = AuthErrorMapper.toUserMessage(
                                error,
                                "No se pudo completar la operación"
                            )
                        )
                    }
                }
        }
    }
}

data class TenantOwnershipUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null
)
