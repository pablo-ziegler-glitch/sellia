package com.example.selliaapp.repository

import com.example.selliaapp.auth.TenantProvider
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

    suspend fun updateSettings(updated: MarketingSettings) {
        val normalizedSettings = updated.copy(
            publicStoreUrl = normalizePublicStoreUrl(updated.publicStoreUrl)
        )

        dataStore.edit { prefs ->
            prefs[Keys.publicStoreUrl] = normalizedSettings.publicStoreUrl
            prefs[Keys.storeName] = normalizedSettings.storeName
            prefs[Keys.storeLogoUrl] = normalizedSettings.storeLogoUrl
            prefs[Keys.storePhone] = normalizedSettings.storePhone
            prefs[Keys.storeWhatsapp] = normalizedSettings.storeWhatsapp
            prefs[Keys.storeEmail] = normalizedSettings.storeEmail
        }

        syncPublicStoreConfig(normalizedSettings)
    }

    private suspend fun syncPublicStoreConfig(settings: MarketingSettings) = withContext(io) {
        runCatching {
            val tenantId = tenantProvider.requireTenantId()
            val publicStoreUrl = settings.publicStoreUrl.trim()
            val publicDomain = extractDomain(publicStoreUrl)
            val updatePayload = mapOf(
                "publicStoreUrl" to publicStoreUrl,
                "publicDomain" to publicDomain,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            firestore.collection("tenants")
                .document(tenantId)
                .collection("config")
                .document("public_store")
                .set(updatePayload, SetOptions.merge())
                .await()

            firestore.collection("tenant_directory")
                .document(tenantId)
                .set(updatePayload, SetOptions.merge())
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
