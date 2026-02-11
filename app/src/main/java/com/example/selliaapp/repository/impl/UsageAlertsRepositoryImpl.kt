package com.example.selliaapp.repository.impl

import com.example.selliaapp.auth.TenantProvider
import com.example.selliaapp.data.model.AlertSeverity
import com.example.selliaapp.data.model.UsageAlert
import com.example.selliaapp.di.AppModule.IoDispatcher
import com.example.selliaapp.repository.UsageAlertsRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageAlertsRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val tenantProvider: TenantProvider,
    private val auth: FirebaseAuth,
    @IoDispatcher private val io: CoroutineDispatcher
) : UsageAlertsRepository {

    override suspend fun fetchAlerts(limit: Int): List<UsageAlert> = withContext(io) {
        val tenantId = tenantProvider.requireTenantId()
        val currentUserId = auth.currentUser?.uid
        val snapshot = firestore.collection("tenants")
            .document(tenantId)
            .collection("alerts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get()
            .await()

        snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            val createdAtMillis = doc.getTimestamp("createdAt")?.toDate()?.time
                ?: (data["createdAtMillis"] as? Number)?.toLong()
            val updatedAtMillis = doc.getTimestamp("updatedAt")?.toDate()?.time
                ?: (data["updatedAtMillis"] as? Number)?.toLong()
            val readBy = (data["readBy"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            UsageAlert(
                id = doc.id,
                title = data["title"] as? String ?: "Alerta",
                message = data["message"] as? String ?: "",
                metric = data["metric"] as? String ?: "",
                percentage = (data["percentage"] as? Number)?.toInt() ?: 0,
                threshold = (data["threshold"] as? Number)?.toInt() ?: 0,
                currentValue = (data["currentValue"] as? Number)?.toDouble() ?: 0.0,
                limitValue = (data["limitValue"] as? Number)?.toDouble() ?: 0.0,
                severity = AlertSeverity.fromRaw(data["severity"] as? String),
                createdAtMillis = createdAtMillis,
                updatedAtMillis = updatedAtMillis,
                isRead = currentUserId != null && readBy.contains(currentUserId),
                periodKey = data["periodKey"] as? String
            )
        }
    }


    override suspend fun fetchCurrentUsageMetrics(): Map<String, Double> = withContext(io) {
        val tenantId = tenantProvider.requireTenantId()
        val currentSnapshot = firestore.collection("tenants")
            .document(tenantId)
            .collection("usageSnapshots")
            .document("current")
            .get()
            .await()

        val data = currentSnapshot.data ?: return@withContext emptyMap()
        val candidateMaps = listOf(
            data["metrics"],
            data["usage"],
            data["counts"]
        ).mapNotNull { value ->
            @Suppress("UNCHECKED_CAST")
            (value as? Map<String, Any?>)?.mapValues { (_, raw) ->
                (raw as? Number)?.toDouble() ?: Double.NaN
            }?.filterValues { it.isFinite() }
        }

        val resolved = candidateMaps.firstOrNull { it.isNotEmpty() }
        if (resolved != null) return@withContext resolved

        data.mapNotNull { (key, value) ->
            val numericValue = (value as? Number)?.toDouble() ?: return@mapNotNull null
            key to numericValue
        }.toMap()
    }

    override suspend fun markAlertRead(alertId: String) = withContext(io) {
        val tenantId = tenantProvider.requireTenantId()
        val userId = auth.currentUser?.uid ?: return@withContext
        firestore.collection("tenants")
            .document(tenantId)
            .collection("alerts")
            .document(alertId)
            .update(
                mapOf(
                    "readBy" to FieldValue.arrayUnion(userId),
                    "readAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
    }

    override suspend fun markAlertsRead(alertIds: List<String>) = withContext(io) {
        if (alertIds.isEmpty()) return@withContext
        val tenantId = tenantProvider.requireTenantId()
        val userId = auth.currentUser?.uid ?: return@withContext
        val batch = firestore.batch()
        alertIds.forEach { alertId ->
            val ref = firestore.collection("tenants")
                .document(tenantId)
                .collection("alerts")
                .document(alertId)
            batch.update(
                ref,
                mapOf(
                    "readBy" to FieldValue.arrayUnion(userId),
                    "readAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.commit().await()
    }
}
