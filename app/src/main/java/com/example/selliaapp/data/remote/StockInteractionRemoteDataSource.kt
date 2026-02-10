package com.example.selliaapp.data.remote

import com.example.selliaapp.auth.TenantProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class StockInteractionEvent(
    val action: String,
    val productId: Int,
    val productName: String?,
    val delta: Int,
    val reason: String,
    val note: String?,
    val source: String,
    val occurredAtEpochMs: Long,
    val actorUid: String? = null
)

class StockInteractionRemoteDataSource(
    private val firestore: FirebaseFirestore,
    private val tenantProvider: TenantProvider
) {
    private suspend fun collection() =
        firestore.collection("tenants")
            .document(tenantProvider.requireTenantId())
            .collection("stock_interactions")

    suspend fun save(events: List<StockInteractionEvent>) {
        if (events.isEmpty()) return
        val col = collection()
        events.chunked(450).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { event ->
                val docRef = col.document("${event.occurredAtEpochMs}-${event.productId}-${UUID.randomUUID()}")
                val payload = mutableMapOf<String, Any?>(
                    "action" to event.action,
                    "productId" to event.productId,
                    "productName" to event.productName,
                    "delta" to event.delta,
                    "reason" to event.reason,
                    "note" to event.note,
                    "source" to event.source,
                    "occurredAt" to event.occurredAtEpochMs,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "actorUid" to event.actorUid
                ).filterValues { it != null }
                batch.set(docRef, payload)
            }
            batch.commit().await()
        }
    }
}
