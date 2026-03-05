package com.floki.app.domain.security

enum class Permission {
    MANAGE_USERS,
    MANAGE_CLOUD_SERVICES,
    VIEW_USAGE_DASHBOARD,
    MANAGE_PUBLICATION,
    MAINTENANCE_READ,
    MAINTENANCE_WRITE,
    CASH_OPEN,
    CASH_AUDIT,
    CASH_MOVEMENT,
    CASH_CLOSE,
    VIEW_CASH_REPORT,
    /** Permite exportar datos con campos PII completos (teléfono, email, dirección de clientes). */
    EXPORT_PII_DATA
}
