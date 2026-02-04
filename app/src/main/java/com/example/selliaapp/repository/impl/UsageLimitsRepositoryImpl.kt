package com.example.selliaapp.repository.impl

import com.example.selliaapp.auth.TenantProvider
import com.example.selliaapp.data.model.usage.UsageLimitOverride
import com.example.selliaapp.di.AppModule.IoDispatcher
import com.example.selliaapp.repository.UsageLimitsRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageLimitsRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val tenantProvider: TenantProvider,
    private val auth: FirebaseAuth,
    @IoDispatcher private val io: CoroutineDispatcher
) : UsageLimitsRepository {

    override suspend fun fetchOverrides(): List<UsageLimitOverride> = withContext(io) {
        val tenantId = tenantProvider.requireTenantId()
        val snapshot = firestore.collection("tenants")
            .document(tenantId)
            .collection("usageLimitOverrides")
            .get()
            .await()

        snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            UsageLimitOverride(
                metric = data["metric"] as? String ?: doc.id,
                limitValue = (data["limitValue"] as? Number)?.toDouble() ?: 0.0,
                updatedAtMillis = doc.getTimestamp("updatedAt")?.toDate()?.time
                    ?: (data["updatedAtMillis"] as? Number)?.toLong(),
                updatedBy = data["updatedBy"] as? String
            )
        }
    }

    override suspend fun updateOverride(metric: String, limitValue: Double): Unit = withContext(io) {
        val tenantId = tenantProvider.requireTenantId()
        val userId = auth.currentUser?.uid
        firestore.collection("tenants")
            .document(tenantId)
            .collection("usageLimitOverrides")
            .document(metric)
            .set(
                mapOf(
                    "metric" to metric,
                    "limitValue" to limitValue,
                    "updatedBy" to userId,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .await()
        Unit
    }
}
