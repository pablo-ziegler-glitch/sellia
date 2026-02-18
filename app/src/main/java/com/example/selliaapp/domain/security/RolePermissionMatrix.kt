package com.example.selliaapp.domain.security

/**
 * Matriz oficial de permisos (fuente única para el producto):
 * - ADMIN: operación completa del tenant y configuración cloud.
 * - OWNER: operación completa del tenant y configuración cloud.
 * - MANAGER: operación diaria de caja + monitoreo de uso, sin administración de cloud ni usuarios.
 * - CASHIER: operación de caja diaria, sin cierre/auditoría avanzada de usuarios/cloud.
 * - VIEWER: cliente final sin permisos operativos internos.
 *
 * Este objeto centraliza el contrato para UI y backend (Cloud Functions) y evita desalineaciones
 * entre capas al consultar siempre el mismo mapa por rol.
 */
object RolePermissionMatrix {
    val byRole: Map<AppRole, Set<Permission>> = mapOf(
        AppRole.ADMIN to Permission.entries.toSet(),
        AppRole.OWNER to Permission.entries.toSet(),
        AppRole.MANAGER to setOf(
            Permission.CASH_OPEN,
            Permission.CASH_AUDIT,
            Permission.CASH_MOVEMENT,
            Permission.CASH_CLOSE,
            Permission.VIEW_CASH_REPORT,
            Permission.VIEW_USAGE_DASHBOARD,
            Permission.MANAGE_PUBLICATION
        ),
        AppRole.CASHIER to setOf(
            Permission.CASH_OPEN,
            Permission.CASH_MOVEMENT,
            Permission.VIEW_CASH_REPORT
        ),
        AppRole.VIEWER to emptySet()
    )
}
