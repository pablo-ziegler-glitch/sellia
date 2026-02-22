package com.example.selliaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.selliaapp.auth.AuthManager
import com.example.selliaapp.auth.AuthState
import com.example.selliaapp.auth.SessionUiNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: AuthManager,
    sessionUiNotifier: SessionUiNotifier
) : ViewModel() {

    val authState: StateFlow<AuthState> = authManager.state
    val loadingUiState = authManager.loadingUiState
    val sessionAlerts: SharedFlow<com.example.selliaapp.auth.SessionUiAlert> = sessionUiNotifier.alerts

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            authManager.signIn(email, password)
        }
    }

    fun signInWithGoogle(idToken: String, allowOnboardingFallback: Boolean = true) {
        viewModelScope.launch {
            authManager.signInWithGoogle(idToken, allowOnboardingFallback)
        }
    }

    fun reportAuthError(message: String) {
        authManager.reportAuthError(message)
    }

    fun signOut() {
        authManager.signOut()
    }

    fun selectTenantForSession(tenantId: String) {
        viewModelScope.launch {
            authManager.switchTenant(tenantId)
        }
    }
}
