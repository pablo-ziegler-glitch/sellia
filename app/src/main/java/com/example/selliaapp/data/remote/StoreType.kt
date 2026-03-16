package com.example.selliaapp.data.remote

enum class StoreType(val raw: String) {
    PHYSICAL("physical"),
    ONLINE("online"),
    BOTH("both");

    companion object {
        fun fromRaw(value: String?): StoreType =
            entries.firstOrNull { it.raw == value?.lowercase() } ?: PHYSICAL
    }
}
