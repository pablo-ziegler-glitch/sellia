package com.floki.app.data.payment

interface PaymentRepository {
    suspend fun createPaymentPreference(request: PaymentPreferenceRequest): PaymentPreferenceResult
}
