package com.example.selliaapp.data.remote

import com.google.firebase.Timestamp

/**
 * Esquemas Firestore para órdenes y pagos.
 *
 * - amount se guarda en centavos para evitar errores de coma flotante.
 * - createdAt/updatedAt deben setearse con serverTimestamp().
 */
object FirestoreCollections {
    const val TENANTS = "tenants"
    const val ORDERS = "orders"
    const val PAYMENTS = "payments"
}

data class OrderFirestoreDocument(
    val status: String = "",
    val amount: Long = 0L,
    val currency: String = "ARS",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val paymentId: String? = null
)

enum class PaymentStatus {
    PENDING,
    APPROVED,
    REJECTED,
    FAILED
}

data class PaymentFirestoreDocument(
    val orderId: String = "",
    val provider: String = "mercado_pago",
    val status: PaymentStatus = PaymentStatus.PENDING,
    val raw: Map<String, Any?> = emptyMap(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
