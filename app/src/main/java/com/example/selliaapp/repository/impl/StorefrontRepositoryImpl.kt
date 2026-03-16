package com.example.selliaapp.repository.impl

import com.example.selliaapp.data.remote.PublicBannerMessage
import com.example.selliaapp.data.remote.StorefrontConfig
import com.example.selliaapp.data.remote.TenantConfigContract
import com.example.selliaapp.di.AppModule
import com.example.selliaapp.repository.StorefrontRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

class StorefrontRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    @AppModule.IoDispatcher private val io: CoroutineDispatcher
) : StorefrontRepository {

    companion object {
        const val DOC_STOREFRONT = "storefront"
        const val COLLECTION_PUBLIC_BANNER = "public_banner_messages"
    }

    override suspend fun getStorefrontConfig(tenantId: String): Result<StorefrontConfig> =
        withContext(io) {
            runCatching {
                val snapshot = firestore
                    .collection(TenantConfigContract.COLLECTION_TENANTS)
                    .document(tenantId)
                    .collection(TenantConfigContract.COLLECTION_CONFIG)
                    .document(DOC_STOREFRONT)
                    .get()
                    .await()
                @Suppress("UNCHECKED_CAST")
                val data = snapshot.data?.get(TenantConfigContract.Fields.DATA) as? Map<String, Any?>
                if (data != null) StorefrontConfig.fromFirestore(data) else StorefrontConfig()
            }
        }

    override suspend fun updateStorefrontConfig(
        tenantId: String,
        config: StorefrontConfig
    ): Result<Unit> = withContext(io) {
        runCatching {
            val payload = mapOf(
                TenantConfigContract.Fields.SCHEMA_VERSION to TenantConfigContract.CURRENT_SCHEMA_VERSION,
                TenantConfigContract.Fields.UPDATED_AT to FieldValue.serverTimestamp(),
                TenantConfigContract.Fields.UPDATED_BY to "android_storefront",
                TenantConfigContract.Fields.DATA to config.toFirestoreMap()
            )
            firestore
                .collection(TenantConfigContract.COLLECTION_TENANTS)
                .document(tenantId)
                .collection(TenantConfigContract.COLLECTION_CONFIG)
                .document(DOC_STOREFRONT)
                .set(payload, SetOptions.merge())
                .await()

            firestore.collection("public_tenant_directory")
                .document(tenantId)
                .set(buildMap {
                    put("name", config.storeName.ifBlank { "" })
                    put("storeType", config.storeType.raw)
                    put("hasDelivery", config.hasDelivery)
                    if (config.locationLat != null && config.locationLng != null) {
                        put("lat", config.locationLat)
                        put("lng", config.locationLng)
                        put("address", config.locationAddress ?: "")
                    }
                    if (!config.locationCity.isNullOrBlank()) put("city", config.locationCity)
                    if (!config.locationCountry.isNullOrBlank()) put("country", config.locationCountry)
                }, SetOptions.merge())
                .await()
            Unit
        }
    }

    override suspend fun fetchActiveBannerMessages(): Result<List<PublicBannerMessage>> =
        withContext(io) {
            runCatching {
                val snapshot = firestore
                    .collection(COLLECTION_PUBLIC_BANNER)
                    .whereEqualTo("active", true)
                    .get()
                    .await()
                snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    PublicBannerMessage(
                        id = doc.id,
                        tenantId = (data["tenantId"] as? String).orEmpty(),
                        storeName = (data["storeName"] as? String).orEmpty(),
                        text = (data["text"] as? String).orEmpty(),
                        active = (data["active"] as? Boolean) ?: true,
                        updatedAt = data["updatedAt"] as? Timestamp
                    )
                }
            }
        }

    override suspend fun syncBannerMessagesToPublic(
        tenantId: String,
        storeName: String,
        config: StorefrontConfig
    ): Result<Unit> = withContext(io) {
        runCatching {
            // Delete existing messages for this tenant
            val existing = firestore.collection(COLLECTION_PUBLIC_BANNER)
                .whereEqualTo("tenantId", tenantId)
                .get()
                .await()
            val batch = firestore.batch()
            existing.documents.forEach { batch.delete(it.reference) }

            // Add active messages (max 3)
            config.bannerMessages
                .filter { it.active }
                .take(3)
                .forEach { msg ->
                    val ref = firestore.collection(COLLECTION_PUBLIC_BANNER).document()
                    batch.set(ref, mapOf(
                        "tenantId" to tenantId,
                        "storeName" to storeName,
                        "text" to msg.text,
                        "active" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))
                }
            batch.commit().await()
            Unit
        }
    }
}
