package com.example.selliaapp.repository

import com.example.selliaapp.domain.usage.FirebaseServiceUsage
import com.example.selliaapp.domain.usage.UsageScope

interface UsageRepository {
    suspend fun getUsageSnapshot(
        tenantId: String,
        appId: String?,
        serviceId: String,
        periodId: String,
        scope: UsageScope
    ): FirebaseServiceUsage?

    suspend fun upsertUsageSnapshot(usage: FirebaseServiceUsage)
}
