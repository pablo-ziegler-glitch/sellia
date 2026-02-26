package com.example.selliaapp.domain.security

/**
 * Matriz mobile_ops:
 * - Canal mobile restringido a capacidades operativas de alta frecuencia (ventas/caja/stock).
 * - Capacidades administrativas de BO (usuarios, cloud, lifecycle, backups) quedan fuera en mobile.
 */
object RolePermissionMatrix {
    val byRole: Map<AppRole, Set<Permission>> = mapOf(
        AppRole.ADMIN to setOf(
            Permission.CASH_OPEN,
            Permission.CASH_AUDIT,
            Permission.CASH_MOVEMENT,
            Permission.CASH_CLOSE,
            Permission.VIEW_CASH_REPORT,
            Permission.MANAGE_PUBLICATION
        ),
        AppRole.OWNER to setOf(
            Permission.CASH_OPEN,
            Permission.CASH_AUDIT,
            Permission.CASH_MOVEMENT,
            Permission.CASH_CLOSE,
            Permission.VIEW_CASH_REPORT,
            Permission.MANAGE_PUBLICATION
        ),
        AppRole.MANAGER to setOf(
            Permission.CASH_OPEN,
            Permission.CASH_AUDIT,
            Permission.CASH_MOVEMENT,
            Permission.CASH_CLOSE,
            Permission.VIEW_CASH_REPORT,
            Permission.MANAGE_PUBLICATION
        ),
        AppRole.CASHIER to setOf(
            Permission.CASH_OPEN,
            Permission.CASH_MOVEMENT,
            Permission.VIEW_CASH_REPORT,
            Permission.MANAGE_PUBLICATION
        ),
        AppRole.VIEWER to emptySet()
    )
}
