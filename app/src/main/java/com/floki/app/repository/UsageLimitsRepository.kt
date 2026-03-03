package com.floki.app.repository

import com.floki.app.data.model.usage.UsageLimitOverride

interface UsageLimitsRepository {
    suspend fun fetchOverrides(): List<UsageLimitOverride>
    suspend fun updateOverride(metric: String, limitValue: Double)
}
