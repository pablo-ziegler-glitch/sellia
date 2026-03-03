package com.floki.app.repository

import com.floki.app.auth.TenantProvider
import com.floki.app.data.dao.CloudServiceConfigDao
import com.floki.app.data.local.entity.CloudServiceConfigEntity
import com.floki.app.data.remote.TenantConfigContract
import com.floki.app.di.AppModule.IoDispatcher
import com.floki.app.domain.config.CloudServiceConfig
import com.floki.app.domain.security.SecurityHashing
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CloudServiceConfigRepository @Inject constructor(
    private val dao: CloudServiceConfigDao,
    private val firestore: FirebaseFirestore,
    private val tenantProvider: TenantProvider,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    fun observeConfigs(): Flow<List<CloudServiceConfig>> =
        dao.observeAll().map { configs -> configs.map { it.toDomain() } }

    suspend fun refreshFromCloud() = withContext(io) {
        runCatching {
            val tenantId = tenantProvider.requireTenantId()
            val snapshot = firestore.collection(TenantConfigContract.COLLECTION_TENANTS)
                .document(tenantId)
                .collection(TenantConfigContract.COLLECTION_CONFIG)
                .document(TenantConfigContract.DOC_CLOUD_SERVICES)
                .get()
                .await()
            val data = snapshot.get(TenantConfigContract.Fields.DATA) as? Map<*, *> ?: return@runCatching
            val perOwner = data["configsByOwner"] as? Map<*, *> ?: return@runCatching
            perOwner.values.forEach { value ->
                val fields = value as? Map<*, *> ?: return@forEach
                val ownerEmail = SecurityHashing.normalizeEmail((fields["ownerEmail"] as? String).orEmpty())
                if (ownerEmail.isBlank()) return@forEach
                dao.upsert(
                    CloudServiceConfigEntity(
                        ownerEmail = ownerEmail,
                        cloudEnabled = fields["cloudEnabled"] as? Boolean ?: false,
                        firestoreBackupEnabled = fields["firestoreBackupEnabled"] as? Boolean ?: false,
                        authSyncEnabled = fields["authSyncEnabled"] as? Boolean ?: false,
                        storageBackupEnabled = fields["storageBackupEnabled"] as? Boolean ?: false,
                        functionsEnabled = fields["functionsEnabled"] as? Boolean ?: false,
                        hostingEnabled = fields["hostingEnabled"] as? Boolean ?: false,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    suspend fun upsert(config: CloudServiceConfig) = withContext(io) {
        val normalized = config.toEntity()
        val ownerEmail = SecurityHashing.normalizeEmail(normalized.ownerEmail)
        val ownerKey = ownerEmail.replace(".", "__dot__")

        runCatching {
            val tenantId = tenantProvider.requireTenantId()
            val payload = mapOf(
                TenantConfigContract.Fields.SCHEMA_VERSION to TenantConfigContract.CURRENT_SCHEMA_VERSION,
                TenantConfigContract.Fields.UPDATED_AT to FieldValue.serverTimestamp(),
                TenantConfigContract.Fields.UPDATED_BY to "android_cloud_services",
                TenantConfigContract.Fields.AUDIT to mapOf(
                    "event" to "UPSERT_CLOUD_SERVICES_CONFIG",
                    "at" to FieldValue.serverTimestamp(),
                    "by" to "android_cloud_services",
                    "ownerEmail" to ownerEmail
                ),
                "${TenantConfigContract.Fields.DATA}.configsByOwner.$ownerKey" to mapOf(
                    "ownerEmail" to ownerEmail,
                    "cloudEnabled" to normalized.cloudEnabled,
                    "firestoreBackupEnabled" to normalized.firestoreBackupEnabled,
                    "authSyncEnabled" to normalized.authSyncEnabled,
                    "storageBackupEnabled" to normalized.storageBackupEnabled,
                    "functionsEnabled" to normalized.functionsEnabled,
                    "hostingEnabled" to normalized.hostingEnabled
                )
            )
            firestore.collection(TenantConfigContract.COLLECTION_TENANTS)
                .document(tenantId)
                .collection(TenantConfigContract.COLLECTION_CONFIG)
                .document(TenantConfigContract.DOC_CLOUD_SERVICES)
                .set(payload, SetOptions.merge())
                .await()
        }

        dao.upsert(normalized)
    }

    suspend fun isCloudEnabled(): Boolean = withContext(io) {
        dao.countCloudEnabled() > 0
    }
}

private fun CloudServiceConfigEntity.toDomain(): CloudServiceConfig =
    CloudServiceConfig(
        ownerEmail = SecurityHashing.normalizeEmail(ownerEmail),
        cloudEnabled = cloudEnabled,
        firestoreBackupEnabled = firestoreBackupEnabled,
        authSyncEnabled = authSyncEnabled,
        storageBackupEnabled = storageBackupEnabled,
        functionsEnabled = functionsEnabled,
        hostingEnabled = hostingEnabled
    )

private fun CloudServiceConfig.toEntity(): CloudServiceConfigEntity =
    CloudServiceConfigEntity(
        ownerEmail = SecurityHashing.normalizeEmail(ownerEmail),
        cloudEnabled = cloudEnabled,
        firestoreBackupEnabled = firestoreBackupEnabled,
        authSyncEnabled = authSyncEnabled,
        storageBackupEnabled = storageBackupEnabled,
        functionsEnabled = functionsEnabled,
        hostingEnabled = hostingEnabled,
        updatedAt = System.currentTimeMillis()
    )
