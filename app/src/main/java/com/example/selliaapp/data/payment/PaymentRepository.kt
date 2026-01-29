package com.example.selliaapp.data.payment

interface PaymentRepository {
    suspend fun createPaymentPreference(request: PaymentPreferenceRequest): PaymentPreferenceResult
}
