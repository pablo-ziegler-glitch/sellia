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
        storePhone: String
    ): Result<OnboardingResult>

    suspend fun registerViewer(
        email: String,
        password: String,
        tenantId: String,
        tenantName: String,
        customerName: String,
        customerPhone: String?
    ): Result<OnboardingResult>

    suspend fun registerViewerWithGoogle(
        idToken: String,
        tenantId: String,
        tenantName: String
    ): Result<OnboardingResult>
}
