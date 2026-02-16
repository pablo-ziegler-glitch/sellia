package com.example.selliaapp.data.payment

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableResult
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MercadoPagoApi @Inject constructor(
    private val functions: FirebaseFunctions,
    private val auth: FirebaseAuth
) {
    suspend fun createPaymentPreference(request: PaymentPreferenceRequest): PaymentPreferenceResult {
        val payload = buildPayload(request)
        val result = callCreatePreferenceWithAuthRecovery(payload)

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

    private suspend fun callCreatePreferenceWithAuthRecovery(payload: Map<String, Any>): HttpsCallableResult {
        return try {
            callCreatePreference(payload)
        } catch (exception: FirebaseFunctionsException) {
            if (exception.code != FirebaseFunctionsException.Code.UNAUTHENTICATED) {
                throw exception
            }

            refreshCallableAuthentication()
            callCreatePreference(payload)
        }
    }

    private suspend fun callCreatePreference(payload: Map<String, Any>): HttpsCallableResult {
        return functions
            .getHttpsCallable(FUNCTION_NAME)
            .call(payload)
            .awaitResult()
    }

    private fun buildPayload(request: PaymentPreferenceRequest): Map<String, Any> {
        val description = request.description.trim()
        val externalReference = request.externalReference.trim()
        val payload = mutableMapOf<String, Any>(
            "amount" to request.amount,
            "description" to description,
            "external_reference" to externalReference,
            "tenantId" to request.tenantId
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
        val metadataWithTenant = request.metadata.toMutableMap().apply {
            put("tenantId", request.tenantId)
        }
        payload["metadata"] = metadataWithTenant

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

    private suspend fun refreshCallableAuthentication() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            auth.signInAnonymously().awaitResult()
            return
        }
        currentUser.getIdToken(true).awaitResult()
    }

    private companion object {
        const val FUNCTION_NAME = "createPaymentPreference"
    }
}
