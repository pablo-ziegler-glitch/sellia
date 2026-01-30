package com.example.selliaapp.data.payment

import com.google.android.gms.tasks.Task
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableResult
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MercadoPagoApi @Inject constructor(
    private val functions: FirebaseFunctions
) {
    suspend fun createPaymentPreference(request: PaymentPreferenceRequest): PaymentPreferenceResult {
        val payload = buildPayload(request)
        val result = functions
            .getHttpsCallable(FUNCTION_NAME)
            .call(payload)
            .awaitResult()

        val data = result.data as? Map<*, *>
            ?: throw IllegalStateException("Respuesta inválida de la Cloud Function de pagos.")

        val initPoint = data["init_point"] as? String
            ?: data["initPoint"] as? String
            ?: throw IllegalStateException("No se recibió init_point de Mercado Pago.")

        return PaymentPreferenceResult(
            initPoint = initPoint,
            preferenceId = data["preference_id"] as? String ?: data["preferenceId"] as? String,
            sandboxInitPoint = data["sandbox_init_point"] as? String
                ?: data["sandboxInitPoint"] as? String
        )
    }

    private fun buildPayload(request: PaymentPreferenceRequest): Map<String, Any> {
        val description = request.description.trim()
        val externalReference = request.externalReference.trim()
        val payload = mutableMapOf<String, Any>(
            "amount" to request.amount,
            "description" to description,
            "external_reference" to externalReference
        )

        if (request.items.isNotEmpty()) {
            payload["items"] = request.items.map { item ->
                mapOf(
                    "id" to item.id,
                    "title" to item.title,
                    "quantity" to item.quantity,
                    "unit_price" to item.unitPrice
                )
            }
        }

        request.payerEmail?.let { payload["payer_email"] = it }
        if (request.metadata.isNotEmpty()) {
            payload["metadata"] = request.metadata
        }

        return payload
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) {
                continuation.resume(result)
            }
        }
        addOnFailureListener { exception ->
            if (continuation.isActive) {
                continuation.resumeWithException(exception)
            }
        }
    }

    private companion object {
        const val FUNCTION_NAME = "createPaymentPreference"
    }
}
