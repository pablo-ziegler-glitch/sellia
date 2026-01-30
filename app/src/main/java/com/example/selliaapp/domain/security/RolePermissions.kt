package com.example.selliaapp.domain.security

object RolePermissions {
    fun forRole(role: AppRole): Set<Permission> = when (role) {
        AppRole.SUPER_ADMIN -> Permission.entries.toSet()
        AppRole.ADMIN -> Permission.entries.toSet()
        AppRole.OWNER -> Permission.entries.toSet()
        AppRole.MANAGER -> setOf(
            Permission.CASH_OPEN,
            Permission.CASH_AUDIT,
            Permission.CASH_MOVEMENT,
            Permission.CASH_CLOSE,
            Permission.VIEW_CASH_REPORT,
            Permission.MANAGE_USERS
        )
        AppRole.CASHIER -> setOf(
            Permission.CASH_OPEN,
            Permission.CASH_AUDIT,
            Permission.CASH_MOVEMENT,
            Permission.VIEW_CASH_REPORT
        )
        AppRole.VIEWER -> setOf(
            Permission.VIEW_CASH_REPORT
        )
    }
}
