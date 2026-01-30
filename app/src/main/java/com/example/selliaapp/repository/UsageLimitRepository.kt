package com.example.selliaapp.repository

import com.example.selliaapp.domain.usage.FreeTierLimit

interface UsageLimitRepository {
    suspend fun getFreeTierLimit(serviceId: String): FreeTierLimit?

    suspend fun upsertFreeTierLimit(limit: FreeTierLimit)
}
