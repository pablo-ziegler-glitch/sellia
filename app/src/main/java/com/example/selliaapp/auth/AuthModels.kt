package com.example.selliaapp.auth

data class AuthSession(
    val uid: String,
    val tenantId: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?
)

sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(
        val session: AuthSession,
        val refreshedAtMs: Long
    ) : AuthState
    data class Error(val message: String) : AuthState
}
