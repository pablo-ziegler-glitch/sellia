package com.example.selliaapp.domain.security

enum class AppRole(val raw: String, val label: String) {
    OWNER("owner", "Dueño/a");

    companion object {
        fun fromRaw(value: String?): AppRole {
            return OWNER
        }
    }
}
