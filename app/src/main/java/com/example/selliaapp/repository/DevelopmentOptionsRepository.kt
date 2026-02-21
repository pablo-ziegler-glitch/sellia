package com.example.selliaapp.repository

import com.example.selliaapp.auth.TenantProvider
import com.example.selliaapp.data.dao.DevelopmentOptionsDao
import com.example.selliaapp.data.local.entity.DevelopmentOptionsEntity
import com.example.selliaapp.data.remote.TenantConfigContract
import com.example.selliaapp.di.AppModule.IoDispatcher
import com.example.selliaapp.domain.config.DevelopmentOptionsConfig
import com.example.selliaapp.domain.security.SecurityHashing
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DevelopmentOptionsRepository @Inject constructor(
    private val dao: DevelopmentOptionsDao,
    private val firestore: FirebaseFirestore,
    private val tenantProvider: TenantProvider,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    fun observeConfigs(): Flow<List<DevelopmentOptionsConfig>> =
        dao.observeAll().map { configs -> configs.map { it.toDomain() } }

    suspend fun refreshFromCloud() = withContext(io) {
        runCatching {
            val tenantId = tenantProvider.requireTenantId()
            val snapshot = firestore.collection(TenantConfigContract.COLLECTION_TENANTS)
                .document(tenantId)
                .collection(TenantConfigContract.COLLECTION_CONFIG)
                .document(TenantConfigContract.DOC_DEVELOPMENT_OPTIONS)
                .get()
                .await()
            val data = snapshot.get(TenantConfigContract.Fields.DATA) as? Map<*, *> ?: return@runCatching
            val perOwner = data["configsByOwner"] as? Map<*, *> ?: return@runCatching
            perOwner.values.forEach { value ->
                val fields = value as? Map<*, *> ?: return@forEach
                val ownerEmail = SecurityHashing.normalizeEmail((fields["ownerEmail"] as? String).orEmpty())
                if (ownerEmail.isBlank()) return@forEach
                dao.upsert(
                    DevelopmentOptionsEntity(
                        ownerEmail = ownerEmail,
                        salesEnabled = fields["salesEnabled"] as? Boolean ?: true,
                        stockEnabled = fields["stockEnabled"] as? Boolean ?: true,
                        customersEnabled = fields["customersEnabled"] as? Boolean ?: true,
                        providersEnabled = fields["providersEnabled"] as? Boolean ?: true,
                        expensesEnabled = fields["expensesEnabled"] as? Boolean ?: true,
                        reportsEnabled = fields["reportsEnabled"] as? Boolean ?: true,
                        cashEnabled = fields["cashEnabled"] as? Boolean ?: true,
                        usageAlertsEnabled = fields["usageAlertsEnabled"] as? Boolean ?: true,
                        configEnabled = fields["configEnabled"] as? Boolean ?: true,
                        publicCatalogEnabled = fields["publicCatalogEnabled"] as? Boolean ?: true,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    suspend fun upsert(config: DevelopmentOptionsConfig) = withContext(io) {
        val normalized = config.toEntity()
        val ownerEmail = SecurityHashing.normalizeEmail(normalized.ownerEmail)
        val ownerKey = ownerEmail.replace(".", "__dot__")

        runCatching {
            val tenantId = tenantProvider.requireTenantId()
            val payload = mapOf(
                TenantConfigContract.Fields.SCHEMA_VERSION to TenantConfigContract.CURRENT_SCHEMA_VERSION,
                TenantConfigContract.Fields.UPDATED_AT to FieldValue.serverTimestamp(),
                TenantConfigContract.Fields.UPDATED_BY to "android_development_options",
                TenantConfigContract.Fields.AUDIT to mapOf(
                    "event" to "UPSERT_DEVELOPMENT_OPTIONS_CONFIG",
                    "at" to FieldValue.serverTimestamp(),
                    "by" to "android_development_options",
                    "ownerEmail" to ownerEmail
                ),
                "${TenantConfigContract.Fields.DATA}.configsByOwner.$ownerKey" to mapOf(
                    "ownerEmail" to ownerEmail,
                    "salesEnabled" to normalized.salesEnabled,
                    "stockEnabled" to normalized.stockEnabled,
                    "customersEnabled" to normalized.customersEnabled,
                    "providersEnabled" to normalized.providersEnabled,
                    "expensesEnabled" to normalized.expensesEnabled,
                    "reportsEnabled" to normalized.reportsEnabled,
                    "cashEnabled" to normalized.cashEnabled,
                    "usageAlertsEnabled" to normalized.usageAlertsEnabled,
                    "configEnabled" to normalized.configEnabled,
                    "publicCatalogEnabled" to normalized.publicCatalogEnabled
                )
            )
            firestore.collection(TenantConfigContract.COLLECTION_TENANTS)
                .document(tenantId)
                .collection(TenantConfigContract.COLLECTION_CONFIG)
                .document(TenantConfigContract.DOC_DEVELOPMENT_OPTIONS)
                .set(payload, SetOptions.merge())
                .await()
        }

        dao.upsert(normalized)
    }
}

private fun DevelopmentOptionsEntity.toDomain(): DevelopmentOptionsConfig =
    DevelopmentOptionsConfig(
        ownerEmail = SecurityHashing.normalizeEmail(ownerEmail),
        salesEnabled = salesEnabled,
        stockEnabled = stockEnabled,
        customersEnabled = customersEnabled,
        providersEnabled = providersEnabled,
        expensesEnabled = expensesEnabled,
        reportsEnabled = reportsEnabled,
        cashEnabled = cashEnabled,
        usageAlertsEnabled = usageAlertsEnabled,
        configEnabled = configEnabled,
        publicCatalogEnabled = publicCatalogEnabled
    )

private fun DevelopmentOptionsConfig.toEntity(): DevelopmentOptionsEntity =
    DevelopmentOptionsEntity(
        ownerEmail = SecurityHashing.normalizeEmail(ownerEmail),
        salesEnabled = salesEnabled,
        stockEnabled = stockEnabled,
        customersEnabled = customersEnabled,
        providersEnabled = providersEnabled,
        expensesEnabled = expensesEnabled,
        reportsEnabled = reportsEnabled,
        cashEnabled = cashEnabled,
        usageAlertsEnabled = usageAlertsEnabled,
        configEnabled = configEnabled,
        publicCatalogEnabled = publicCatalogEnabled,
        updatedAt = System.currentTimeMillis()
    )
