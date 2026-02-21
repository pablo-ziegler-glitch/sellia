package com.example.selliaapp.repository

import com.example.selliaapp.auth.TenantProvider
import com.example.selliaapp.data.remote.TenantConfigContract
import com.example.selliaapp.di.AppModule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class MarketingSettings(
    val publicStoreUrl: String = "",
    val storeName: String = "Tu tienda",
    val storeLogoUrl: String = "",
    val storePhone: String = "",
    val storeWhatsapp: String = "",
    val storeEmail: String = ""
)

class MarketingConfigRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val firestore: FirebaseFirestore,
    private val tenantProvider: TenantProvider,
    @AppModule.IoDispatcher private val io: CoroutineDispatcher
) {
    private object Keys {
        val publicStoreUrl = stringPreferencesKey("public_store_url")
        val storeName = stringPreferencesKey("store_name")
        val storeLogoUrl = stringPreferencesKey("store_logo_url")
        val storePhone = stringPreferencesKey("store_phone")
        val storeWhatsapp = stringPreferencesKey("store_whatsapp")
        val storeEmail = stringPreferencesKey("store_email")
    }

    val settings: Flow<MarketingSettings> = dataStore.data.map { prefs ->
        MarketingSettings(
            publicStoreUrl = prefs[Keys.publicStoreUrl] ?: "",
            storeName = prefs[Keys.storeName] ?: "Tu tienda",
            storeLogoUrl = prefs[Keys.storeLogoUrl] ?: "",
            storePhone = prefs[Keys.storePhone] ?: "",
            storeWhatsapp = prefs[Keys.storeWhatsapp] ?: "",
            storeEmail = prefs[Keys.storeEmail] ?: ""
        )
    }

    suspend fun refreshFromCloud() = withContext(io) {
        runCatching {
            val tenantId = tenantProvider.requireTenantId()
            val snapshot = firestore.collection(TenantConfigContract.COLLECTION_TENANTS)
                .document(tenantId)
                .collection(TenantConfigContract.COLLECTION_CONFIG)
                .document(TenantConfigContract.DOC_MARKETING)
                .get()
                .await()
            val data = snapshot.get(TenantConfigContract.Fields.DATA) as? Map<*, *> ?: return@runCatching
            val cloudSettings = MarketingSettings(
                publicStoreUrl = (data["publicStoreUrl"] as? String).orEmpty(),
                storeName = (data["storeName"] as? String).orEmpty().ifBlank { "Tu tienda" },
                storeLogoUrl = (data["storeLogoUrl"] as? String).orEmpty(),
                storePhone = (data["storePhone"] as? String).orEmpty(),
                storeWhatsapp = (data["storeWhatsapp"] as? String).orEmpty(),
                storeEmail = (data["storeEmail"] as? String).orEmpty()
            )
            dataStore.edit { prefs ->
                prefs[Keys.publicStoreUrl] = normalizePublicStoreUrl(cloudSettings.publicStoreUrl)
                prefs[Keys.storeName] = cloudSettings.storeName
                prefs[Keys.storeLogoUrl] = cloudSettings.storeLogoUrl
                prefs[Keys.storePhone] = cloudSettings.storePhone
                prefs[Keys.storeWhatsapp] = cloudSettings.storeWhatsapp
                prefs[Keys.storeEmail] = cloudSettings.storeEmail
            }
        }
    }

    suspend fun updateSettings(updated: MarketingSettings) {
        val normalizedSettings = updated.copy(
            publicStoreUrl = normalizePublicStoreUrl(updated.publicStoreUrl)
        )

        syncPublicStoreConfig(normalizedSettings)

        dataStore.edit { prefs ->
            prefs[Keys.publicStoreUrl] = normalizedSettings.publicStoreUrl
            prefs[Keys.storeName] = normalizedSettings.storeName
            prefs[Keys.storeLogoUrl] = normalizedSettings.storeLogoUrl
            prefs[Keys.storePhone] = normalizedSettings.storePhone
            prefs[Keys.storeWhatsapp] = normalizedSettings.storeWhatsapp
            prefs[Keys.storeEmail] = normalizedSettings.storeEmail
        }

    }

    private suspend fun syncPublicStoreConfig(settings: MarketingSettings) = withContext(io) {
        runCatching {
            val tenantId = tenantProvider.requireTenantId()
            val publicStoreUrl = settings.publicStoreUrl.trim()
            val publicDomain = extractDomain(publicStoreUrl)
            val updatePayload = mapOf(
                TenantConfigContract.Fields.SCHEMA_VERSION to TenantConfigContract.CURRENT_SCHEMA_VERSION,
                TenantConfigContract.Fields.UPDATED_AT to FieldValue.serverTimestamp(),
                TenantConfigContract.Fields.UPDATED_BY to "android_marketing",
                TenantConfigContract.Fields.AUDIT to mapOf(
                    "event" to "UPSERT_MARKETING_CONFIG",
                    "at" to FieldValue.serverTimestamp(),
                    "by" to "android_marketing"
                ),
                TenantConfigContract.Fields.DATA to mapOf(
                    "publicStoreUrl" to publicStoreUrl,
                    "publicDomain" to publicDomain,
                    "storeName" to settings.storeName,
                    "storeLogoUrl" to settings.storeLogoUrl,
                    "storePhone" to settings.storePhone,
                    "storeWhatsapp" to settings.storeWhatsapp,
                    "storeEmail" to settings.storeEmail
                )
            )

            firestore.collection(TenantConfigContract.COLLECTION_TENANTS)
                .document(tenantId)
                .collection(TenantConfigContract.COLLECTION_CONFIG)
                .document(TenantConfigContract.DOC_MARKETING)
                .set(updatePayload, SetOptions.merge())
                .await()

            firestore.collection("tenant_directory")
                .document(tenantId)
                .set(
                    mapOf(
                        "publicStoreUrl" to publicStoreUrl,
                        "publicDomain" to publicDomain,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .await()
        }
    }

    private fun normalizePublicStoreUrl(rawValue: String): String {
        val trimmed = rawValue.trim()
        if (trimmed.isBlank()) return ""
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private fun extractDomain(publicStoreUrl: String): String {
        val normalized = normalizePublicStoreUrl(publicStoreUrl)
        if (normalized.isBlank()) return ""
        return runCatching {
            java.net.URI(normalized).host.orEmpty().removePrefix("www.")
        }.getOrDefault("")
    }
}
