package com.example.selliaapp.repository

import com.example.selliaapp.data.model.usage.UsageDashboardSnapshot
import com.example.selliaapp.domain.usage.FirebaseServiceUsage
import com.example.selliaapp.domain.usage.UsageScope
import java.time.LocalDate

interface UsageRepository {
    suspend fun getUsageSnapshot(
        tenantId: String,
        appId: String?,
        serviceId: String,
        periodId: String,
        scope: UsageScope
    ): FirebaseServiceUsage?

    suspend fun upsertUsageSnapshot(usage: FirebaseServiceUsage)

    suspend fun getUsageDashboard(from: LocalDate, to: LocalDate): UsageDashboardSnapshot =
        throw UnsupportedOperationException("Dashboard de consumo no implementado")
}
