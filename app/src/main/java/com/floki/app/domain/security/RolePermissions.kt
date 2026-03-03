package com.floki.app.domain.security

object RolePermissions {
    fun forRole(role: AppRole): Set<Permission> =
        RolePermissionMatrix.byRole[role] ?: emptySet()
}
