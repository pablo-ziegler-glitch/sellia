package com.example.selliaapp.repository

data class OnboardingResult(
    val uid: String,
    val tenantId: String
)

interface AuthOnboardingRepository {
    suspend fun registerStore(
        email: String,
        password: String,
        storeName: String,
        storeAddress: String,
        storePhone: String,
        skuPrefix: String?
    ): Result<OnboardingResult>
}
