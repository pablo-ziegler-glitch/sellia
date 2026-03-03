package com.floki.app.domain.payment

import com.floki.app.data.payment.PaymentItem
import com.floki.app.data.payment.PaymentPreferenceRequest
import com.floki.app.data.payment.PaymentPreferenceResult
import com.floki.app.data.payment.PaymentRepository
import javax.inject.Inject

class CreatePaymentPreferenceUseCase @Inject constructor(
    private val paymentRepository: PaymentRepository
) {
    suspend operator fun invoke(
        amount: Double,
        description: String,
        externalReference: String,
        tenantId: String,
        items: List<PaymentItem> = emptyList(),
        payerEmail: String? = null,
        metadata: Map<String, Any?> = emptyMap()
    ): PaymentPreferenceResult {
        require(amount > 0) { "El monto debe ser mayor a cero." }
        require(description.isNotBlank()) { "La descripción del pago es obligatoria." }
        require(externalReference.isNotBlank()) { "Se requiere una referencia externa." }
        require(tenantId.isNotBlank()) { "Se requiere tenantId para crear el pago." }

        val request = PaymentPreferenceRequest(
            amount = amount,
            description = description.trim(),
            externalReference = externalReference,
            tenantId = tenantId.trim(),
            items = items,
            payerEmail = payerEmail,
            metadata = metadata
        )

        return paymentRepository.createPaymentPreference(request)
    }
}
