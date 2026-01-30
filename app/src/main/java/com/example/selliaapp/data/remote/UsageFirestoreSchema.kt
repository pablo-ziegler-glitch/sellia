package com.example.selliaapp.data.remote

import com.google.firebase.Timestamp

/**
 * Esquema Firestore para consumo y límites.
 *
 * Colecciones principales:
 * - /usage/{tenantId}/services/{serviceId}/snapshots/{periodId}
 *   -> agregado global por tenant (multi-app) y período.
 * - /usage/{tenantId}/apps/{appId}/services/{serviceId}/snapshots/{periodId}
 *   -> consumo por aplicación/proyecto dentro del tenant.
 * - /usageLimits/{serviceId}
 *   -> umbrales free tier por servicio (referenciados al pricing oficial).
 */
object UsageFirestoreSchema {
    const val COLLECTION_USAGE = "usage"
    const val COLLECTION_APPS = "apps"
    const val COLLECTION_SERVICES = "services"
    const val COLLECTION_SNAPSHOTS = "snapshots"
    const val COLLECTION_LIMITS = "usageLimits"
}

data class UsageSnapshotDocument(
    val tenantId: String = "",
    val appId: String? = null,
    val serviceId: String = "",
    val scope: String = "TENANT",
    val periodId: String = "",
    val periodStartMillis: Long = 0L,
    val periodEndMillis: Long = 0L,
    val metrics: Map<String, Long> = emptyMap(),
    val updatedAt: Timestamp? = null
)

data class FreeTierLimitDocument(
    val serviceId: String = "",
    val period: String = "MONTHLY",
    val limits: Map<String, Long> = emptyMap(),
    val sourceUrl: String = "",
    val updatedAt: Timestamp? = null
)
