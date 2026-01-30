package com.example.selliaapp.data.remote

import com.example.selliaapp.di.AppModule.IoDispatcher
import com.example.selliaapp.domain.usage.FirebaseServiceUsage
import com.example.selliaapp.domain.usage.FreeTierLimit
import com.example.selliaapp.domain.usage.UsageScope
import com.example.selliaapp.repository.UsageLimitRepository
import com.example.selliaapp.repository.UsageRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreUsageRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    @IoDispatcher private val io: CoroutineDispatcher
) : UsageRepository, UsageLimitRepository {

    override suspend fun getUsageSnapshot(
        tenantId: String,
        appId: String?,
        serviceId: String,
        periodId: String,
        scope: UsageScope
    ): FirebaseServiceUsage? = withContext(io) {
        val resolvedScope = resolveScope(scope = scope, appId = appId)
        val docRef = usageSnapshotRef(
            tenantId = tenantId,
            appId = appId,
            serviceId = serviceId,
            periodId = periodId,
            scope = resolvedScope
        )
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) return@withContext null
        val document = snapshot.toObject(UsageSnapshotDocument::class.java)
            ?: return@withContext null
        document.toDomain(defaultTenantId = tenantId, defaultAppId = appId)
    }

    override suspend fun upsertUsageSnapshot(usage: FirebaseServiceUsage) {
        withContext(io) {
            val docRef = usageSnapshotRef(
                tenantId = usage.tenantId,
                appId = usage.appId,
                serviceId = usage.serviceId,
                periodId = usage.snapshot.periodId,
                scope = usage.scope
            )
            val data = usage.toDocument().toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.set(data).await()
        }
    }

    override suspend fun getFreeTierLimit(serviceId: String): FreeTierLimit? = withContext(io) {
        val snapshot = firestore.collection(UsageFirestoreSchema.COLLECTION_LIMITS)
            .document(serviceId)
            .get()
            .await()
        if (!snapshot.exists()) return@withContext null
        val document = snapshot.toObject(FreeTierLimitDocument::class.java)
            ?: return@withContext null
        document.toDomain()
    }

    override suspend fun upsertFreeTierLimit(limit: FreeTierLimit) {
        withContext(io) {
            val data = limit.toDocument().toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            firestore.collection(UsageFirestoreSchema.COLLECTION_LIMITS)
                .document(limit.serviceId)
                .set(data)
                .await()
        }
    }

    private fun usageSnapshotRef(
        tenantId: String,
        appId: String?,
        serviceId: String,
        periodId: String,
        scope: UsageScope
    ) = when (scope) {
        UsageScope.APP -> firestore.collection(UsageFirestoreSchema.COLLECTION_USAGE)
            .document(tenantId)
            .collection(UsageFirestoreSchema.COLLECTION_APPS)
            .document(requireNotNull(appId) { "appId requerido para scope APP" })
            .collection(UsageFirestoreSchema.COLLECTION_SERVICES)
            .document(serviceId)
            .collection(UsageFirestoreSchema.COLLECTION_SNAPSHOTS)
            .document(periodId)

        UsageScope.TENANT -> firestore.collection(UsageFirestoreSchema.COLLECTION_USAGE)
            .document(tenantId)
            .collection(UsageFirestoreSchema.COLLECTION_SERVICES)
            .document(serviceId)
            .collection(UsageFirestoreSchema.COLLECTION_SNAPSHOTS)
            .document(periodId)
    }

    private fun resolveScope(scope: UsageScope, appId: String?): UsageScope = when {
        appId.isNullOrBlank() -> UsageScope.TENANT
        else -> scope
    }
}

private fun UsageSnapshotDocument.toDomain(
    defaultTenantId: String,
    defaultAppId: String?
): FirebaseServiceUsage {
    val resolvedScope = UsageScope.valueOf(scope)
    return FirebaseServiceUsage(
        tenantId = tenantId.ifBlank { defaultTenantId },
        appId = appId ?: defaultAppId,
        serviceId = serviceId,
        scope = resolvedScope,
        snapshot = com.example.selliaapp.domain.usage.UsageSnapshot(
            periodId = periodId,
            periodStartMillis = periodStartMillis,
            periodEndMillis = periodEndMillis,
            metrics = metrics,
            recordedAtMillis = updatedAt?.toDate()?.time ?: 0L
        )
    )
}

private fun FirebaseServiceUsage.toDocument(): Map<String, Any?> = mapOf(
    "tenantId" to tenantId,
    "appId" to appId,
    "serviceId" to serviceId,
    "scope" to scope.name,
    "periodId" to snapshot.periodId,
    "periodStartMillis" to snapshot.periodStartMillis,
    "periodEndMillis" to snapshot.periodEndMillis,
    "metrics" to snapshot.metrics
)

private fun FreeTierLimitDocument.toDomain(): FreeTierLimit = FreeTierLimit(
    serviceId = serviceId,
    period = com.example.selliaapp.domain.usage.UsagePeriod.valueOf(period),
    limits = limits,
    sourceUrl = sourceUrl,
    updatedAtMillis = updatedAt?.toDate()?.time ?: 0L
)

private fun FreeTierLimit.toDocument(): Map<String, Any?> = mapOf(
    "serviceId" to serviceId,
    "period" to period.name,
    "limits" to limits,
    "sourceUrl" to sourceUrl
)
