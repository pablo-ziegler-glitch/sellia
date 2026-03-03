package com.floki.app.repository

import com.floki.app.domain.usage.FreeTierLimit

interface UsageLimitRepository {
    suspend fun getFreeTierLimit(serviceId: String): FreeTierLimit?

    suspend fun upsertFreeTierLimit(limit: FreeTierLimit)
}
