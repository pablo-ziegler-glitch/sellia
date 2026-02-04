package com.example.selliaapp.repository.impl

import com.example.selliaapp.data.model.usage.UsageDashboardSnapshot
import com.example.selliaapp.data.model.usage.UsageSeriesPoint
import com.example.selliaapp.data.model.usage.UsageServiceSummary
import com.example.selliaapp.data.remote.UsageFirestoreSchema
import com.example.selliaapp.data.remote.UsageSnapshotDocument
import com.example.selliaapp.di.AppModule.IoDispatcher
import com.example.selliaapp.domain.usage.FirebaseServiceUsage
import com.example.selliaapp.domain.usage.UsageScope
import com.example.selliaapp.repository.UsageRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : UsageRepository {

    override suspend fun getUsageSnapshot(
        tenantId: String,
        appId: String?,
        serviceId: String,
        periodId: String,
        scope: UsageScope
    ): FirebaseServiceUsage? = withContext(ioDispatcher) {
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
        withContext(ioDispatcher) {
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

    override suspend fun getUsageDashboard(from: LocalDate, to: LocalDate): UsageDashboardSnapshot =
        withContext(ioDispatcher) {
            val zone = ZoneId.systemDefault()
            val startTimestamp = from.atStartOfDay(zone).toInstant().let { Timestamp(Date.from(it)) }
            val endTimestamp = to.plusDays(1).atStartOfDay(zone).minusNanos(1)
                .toInstant()
                .let { Timestamp(Date.from(it)) }

            val seriesSnapshot = firestore.collection(COLLECTION_USAGE_SERIES)
                .whereGreaterThanOrEqualTo(FIELD_DATE, startTimestamp)
                .whereLessThanOrEqualTo(FIELD_DATE, endTimestamp)
                .orderBy(FIELD_DATE)
                .get()
                .await()

            val series = seriesSnapshot.documents.mapNotNull { doc ->
                val timestamp = doc.getTimestamp(FIELD_DATE) ?: return@mapNotNull null
                val value = doc.getDouble(FIELD_VALUE) ?: 0.0
                val date = Instant.ofEpochSecond(timestamp.seconds, timestamp.nanoseconds.toLong())
                    .atZone(zone)
                    .toLocalDate()
                UsageSeriesPoint(date = date, value = value)
            }

            val servicesSnapshot = firestore.collection(COLLECTION_USAGE_SERVICES)
                .orderBy(FIELD_TOTAL, Query.Direction.DESCENDING)
                .limit(MAX_SERVICES)
                .get()
                .await()

            val services = servicesSnapshot.documents.map { doc ->
                UsageServiceSummary(
                    serviceName = doc.getString(FIELD_SERVICE).orEmpty(),
                    appName = doc.getString(FIELD_APP).orEmpty(),
                    total = doc.getDouble(FIELD_TOTAL) ?: 0.0,
                    trendPercent = doc.getDouble(FIELD_TREND) ?: 0.0,
                    sharePercent = doc.getDouble(FIELD_SHARE) ?: 0.0
                )
            }

            val total = services.sumOf { it.total }.takeIf { it > 0.0 }
                ?: series.sumOf { it.value }

            UsageDashboardSnapshot(
                from = from,
                to = to,
                total = total,
                series = series,
                services = services,
                lastUpdated = Instant.now()
            )
        }

    private companion object {
        const val COLLECTION_USAGE_SERIES = "usage_series"
        const val COLLECTION_USAGE_SERVICES = "usage_services"
        const val FIELD_DATE = "date"
        const val FIELD_VALUE = "value"
        const val FIELD_SERVICE = "serviceName"
        const val FIELD_APP = "appName"
        const val FIELD_TOTAL = "total"
        const val FIELD_TREND = "trendPercent"
        const val FIELD_SHARE = "sharePercent"
        const val MAX_SERVICES = 8L
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
