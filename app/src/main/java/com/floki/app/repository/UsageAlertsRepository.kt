package com.floki.app.repository

import com.floki.app.data.model.UsageAlert

interface UsageAlertsRepository {
    suspend fun fetchAlerts(limit: Int = 50): List<UsageAlert>
    suspend fun fetchCurrentUsageMetrics(): Map<String, Double>
    suspend fun markAlertRead(alertId: String)
    suspend fun markAlertsRead(alertIds: List<String>)
}
