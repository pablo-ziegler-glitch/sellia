package com.example.selliaapp.repository

import com.example.selliaapp.data.model.usage.UsageLimitOverride

interface UsageLimitsRepository {
    suspend fun fetchOverrides(): List<UsageLimitOverride>
    suspend fun updateOverride(metric: String, limitValue: Double)
}
