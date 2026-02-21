package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.auth.AuthManager
import com.example.selliaapp.repository.SecurityConfigRepository
import com.example.selliaapp.repository.SecuritySettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SecuritySettingsViewModel @Inject constructor(
    private val securityConfigRepository: SecurityConfigRepository,
    private val authManager: AuthManager
 ) : ViewModel() {
    val settings: StateFlow<SecuritySettings> = securityConfigRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SecuritySettings()
    )

    init {
        viewModelScope.launch { securityConfigRepository.refreshFromCloud() }
    }

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    fun updateAdminEmail(email: String) {
        viewModelScope.launch {
            val trimmed = email.trim()
            if (trimmed.isBlank() || !trimmed.contains("@")) {
                _events.emit("Ingresá un email válido para el admin.")
                return@launch
            }
            runCatching { securityConfigRepository.updateAdminEmail(trimmed) }
                .onSuccess { _events.emit("Email admin actualizado.") }
                .onFailure { _events.emit("No se pudo actualizar el email admin.") }
        }
    }

    fun updatePassword(newPassword: String, confirmPassword: String) {
        viewModelScope.launch {
            val trimmed = newPassword.trim()
            if (trimmed.length < 6) {
                _events.emit("La contraseña debe tener al menos 6 caracteres.")
                return@launch
            }
            if (trimmed != confirmPassword.trim()) {
                _events.emit("Las contraseñas no coinciden.")
                return@launch
            }
            authManager.updatePassword(trimmed)
                .onSuccess { _events.emit("Contraseña actualizada.") }
                .onFailure { _events.emit("No se pudo actualizar la contraseña.") }
        }
    }
}
