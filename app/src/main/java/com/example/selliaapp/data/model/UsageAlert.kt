package com.example.selliaapp.data.model

enum class AlertSeverity(val raw: String, val label: String) {
    WARNING("warning", "Advertencia"),
    HIGH("high", "Alta"),
    CRITICAL("critical", "Crítica"),
    INFO("info", "Info");

    companion object {
        fun fromRaw(value: String?): AlertSeverity {
            val normalized = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.raw == normalized } ?: INFO
        }
    }
}

data class UsageAlert(
    val id: String,
    val title: String,
    val message: String,
    val metric: String,
    val percentage: Int,
    val threshold: Int,
    val currentValue: Double,
    val limitValue: Double,
    val severity: AlertSeverity,
    val createdAtMillis: Long?,
    val updatedAtMillis: Long?,
    val isRead: Boolean,
    val periodKey: String?
)
