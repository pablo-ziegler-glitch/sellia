package com.example.selliaapp.data.payment

/**
 * Datos mínimos para crear una preferencia de pago en Mercado Pago.
 * Todo se envía a la Cloud Function que conversa con la API real.
 */
data class PaymentPreferenceRequest(
    val amount: Double,
    val description: String,
    val externalReference: String,
    val tenantId: String,
    val items: List<PaymentItem> = emptyList(),
    val payerEmail: String? = null,
    val metadata: Map<String, Any?> = emptyMap()
)

data class PaymentItem(
    val id: String,
    val title: String,
    val quantity: Int,
    val unitPrice: Double
)

data class PaymentPreferenceResult(
    val initPoint: String,
    val preferenceId: String? = null,
    val sandboxInitPoint: String? = null,
    val orderId: String? = null,
    val idempotencyKey: String? = null,
    val paymentStatus: String? = null
)
