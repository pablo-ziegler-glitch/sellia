package com.floki.app.data.payment

import javax.inject.Inject

class PaymentRepositoryImpl @Inject constructor(
    private val mercadoPagoApi: MercadoPagoApi
) : PaymentRepository {
    override suspend fun createPaymentPreference(request: PaymentPreferenceRequest): PaymentPreferenceResult =
        mercadoPagoApi.createPaymentPreference(request)
}
