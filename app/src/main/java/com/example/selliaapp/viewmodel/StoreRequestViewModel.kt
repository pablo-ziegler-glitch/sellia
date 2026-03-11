package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.data.remote.StoreRequest
import com.example.selliaapp.data.remote.StoreRequestStatus
import com.example.selliaapp.repository.StoreRequestRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StoreRequestUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val myRequests: List<StoreRequest> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val hasPendingRequest: Boolean = false
)

@HiltViewModel
class StoreRequestViewModel @Inject constructor(
    private val storeRequestRepository: StoreRequestRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoreRequestUiState())
    val uiState: StateFlow<StoreRequestUiState> = _uiState

    init {
        loadMyRequests()
    }

    fun loadMyRequests() {
        val userId = auth.currentUser?.uid ?: return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            storeRequestRepository.getMyRequests(userId)
                .onSuccess { requests ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            myRequests = requests,
                            hasPendingRequest = requests.any { r ->
                                r.status == StoreRequestStatus.PENDING
                            }
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message)
                    }
                }
        }
    }

    fun submitRequest(
        storeName: String,
        storeDescription: String,
        storePhone: String
    ) {
        val user = auth.currentUser ?: return
        if (storeName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Ingresá el nombre de tu tienda") }
            return
        }
        if (storePhone.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Ingresá un teléfono de contacto") }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            val request = StoreRequest(
                userId = user.uid,
                email = user.email.orEmpty(),
                storeName = storeName.trim(),
                storeDescription = storeDescription.trim(),
                storePhone = storePhone.trim(),
                status = StoreRequestStatus.PENDING
            )
            storeRequestRepository.submitRequest(request)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            successMessage = "Solicitud enviada. Te notificaremos cuando sea revisada.",
                            hasPendingRequest = true
                        )
                    }
                    loadMyRequests()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = error.message ?: "Error al enviar solicitud")
                    }
                }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
