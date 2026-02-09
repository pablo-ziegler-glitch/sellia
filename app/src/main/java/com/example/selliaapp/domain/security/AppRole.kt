package com.example.selliaapp.domain.security

enum class AppRole(val raw: String, val label: String) {
    SUPER_ADMIN("super_admin", "Super administrador/a"),
    ADMIN("admin", "Administrador/a"),
    OWNER("owner", "Dueño/a"),
    MANAGER("manager", "Encargado/a"),
    CASHIER("cashier", "Cajero/a"),
    VIEWER("viewer", "Cliente final");

    companion object {
        fun fromRaw(value: String?): AppRole {
            val normalized = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.raw == normalized } ?: VIEWER
        }
    }
}
