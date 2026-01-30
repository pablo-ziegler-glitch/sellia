package com.example.selliaapp.domain.usage

/**
 * Keys estándar para métricas de uso. Permite reusar los mismos nombres en consumo y límites.
 */
object UsageMetricKey {
    const val FIRESTORE_READS = "firestore_reads"
    const val FIRESTORE_WRITES = "firestore_writes"
    const val FIRESTORE_DELETES = "firestore_deletes"
    const val FIRESTORE_STORAGE_BYTES = "firestore_storage_bytes"
    const val FIRESTORE_BANDWIDTH_BYTES = "firestore_bandwidth_bytes"
    const val AUTH_MONTHLY_ACTIVE_USERS = "auth_monthly_active_users"
    const val STORAGE_BYTES = "storage_bytes"
    const val STORAGE_BANDWIDTH_BYTES = "storage_bandwidth_bytes"
    const val FUNCTIONS_INVOCATIONS = "functions_invocations"
    const val FUNCTIONS_COMPUTE_MILLIS = "functions_compute_millis"
    const val HOSTING_BANDWIDTH_BYTES = "hosting_bandwidth_bytes"
}

enum class UsageScope {
    TENANT,
    APP
}

enum class UsagePeriod {
    DAILY,
    MONTHLY
}

data class UsageSnapshot(
    val periodId: String,
    val periodStartMillis: Long,
    val periodEndMillis: Long,
    val metrics: Map<String, Long>,
    val recordedAtMillis: Long
)

data class FirebaseServiceUsage(
    val tenantId: String,
    val appId: String?,
    val serviceId: String,
    val scope: UsageScope,
    val snapshot: UsageSnapshot
)

data class FreeTierLimit(
    val serviceId: String,
    val period: UsagePeriod,
    val limits: Map<String, Long>,
    val sourceUrl: String,
    val updatedAtMillis: Long
)
