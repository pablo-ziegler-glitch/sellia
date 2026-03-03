package com.floki.app.repository

import com.floki.app.data.model.usage.UsageDashboardSnapshot
import com.floki.app.domain.usage.FirebaseServiceUsage
import com.floki.app.domain.usage.UsageScope
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
