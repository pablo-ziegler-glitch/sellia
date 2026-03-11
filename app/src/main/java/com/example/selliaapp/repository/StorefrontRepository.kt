package com.example.selliaapp.repository

import com.example.selliaapp.data.remote.PublicBannerMessage
import com.example.selliaapp.data.remote.StorefrontConfig

interface StorefrontRepository {
    suspend fun getStorefrontConfig(tenantId: String): Result<StorefrontConfig>
    suspend fun updateStorefrontConfig(tenantId: String, config: StorefrontConfig): Result<Unit>
    suspend fun fetchActiveBannerMessages(): Result<List<PublicBannerMessage>>
    suspend fun syncBannerMessagesToPublic(tenantId: String, storeName: String, config: StorefrontConfig): Result<Unit>
}
