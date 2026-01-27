package com.example.selliaapp.domain.security

data class UserAccessState(
    val email: String?,
    val role: AppRole,
    val permissions: Set<Permission>
) {
    companion object {
        fun guest(): UserAccessState {
            val role = AppRole.VIEWER
            return UserAccessState(email = null, role = role, permissions = RolePermissions.forRole(role))
        }
    }
}
