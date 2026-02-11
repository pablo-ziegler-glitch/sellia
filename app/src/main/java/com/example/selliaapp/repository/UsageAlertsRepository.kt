package com.example.selliaapp.repository

import com.example.selliaapp.data.model.UsageAlert

interface UsageAlertsRepository {
    suspend fun fetchAlerts(limit: Int = 50): List<UsageAlert>
    suspend fun fetchCurrentUsageMetrics(): Map<String, Double>
    suspend fun markAlertRead(alertId: String)
    suspend fun markAlertsRead(alertIds: List<String>)
}
