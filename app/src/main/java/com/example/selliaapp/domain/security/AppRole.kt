package com.example.selliaapp.domain.security

enum class AppRole(val raw: String, val label: String) {
    ADMIN("admin", "Administrador/a"),
    OWNER("owner", "Dueño/a"),
    MANAGER("manager", "Encargado/a"),
    CASHIER("cashier", "Cajero/a"),
    VIEWER("viewer", "Cliente final");

    companion object {
        fun fromRaw(value: String?): AppRole {
            val normalized = value?.trim()?.lowercase().orEmpty()
            if (normalized == "super_admin") {
                return ADMIN
            }
            return entries.firstOrNull { it.raw == normalized } ?: VIEWER
        }
    }
}
