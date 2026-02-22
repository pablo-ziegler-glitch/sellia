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
    val photoUrl: String?,
    val availableTenants: List<PendingTenantOption> = emptyList()
)

data class PendingTenantOption(
    val id: String,
    val name: String
)

enum class RequiredAuthAction {
    SELECT_TENANT
}

sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class PartiallyAuthenticated(
        val session: PendingAuthSession,
        val requiredAction: RequiredAuthAction
    ) : AuthState
    data class Authenticated(
        val session: AuthSession,
        val refreshedAtMs: Long
    ) : AuthState
    data class Error(val message: String) : AuthState
}
