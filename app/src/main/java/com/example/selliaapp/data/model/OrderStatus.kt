package com.example.selliaapp.data.model

/**
 * Estado actual de una orden de Checkout Pro (Firestore).
 * Se consulta on-demand (get puntual) para minimizar costos y listeners globales.
 */
data class OrderStatus(
    val orderId: String,
    val status: String,
    val paymentStatus: String? = null,
    val statusDetail: String? = null,
    val updatedAtMillis: Long? = null
)
