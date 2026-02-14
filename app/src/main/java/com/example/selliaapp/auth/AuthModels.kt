package com.example.selliaapp.auth

data class AuthSession(
    val uid: String,
    val tenantId: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?
)

data class PendingAuthSession(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?
)

enum class RequiredAuthAction {
    SELECT_TENANT
}

sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val session: AuthSession) : AuthState
    data class PartiallyAuthenticated(
        val session: PendingAuthSession,
        val requiredAction: RequiredAuthAction
    ) : AuthState
    data class Error(val message: String) : AuthState
}
