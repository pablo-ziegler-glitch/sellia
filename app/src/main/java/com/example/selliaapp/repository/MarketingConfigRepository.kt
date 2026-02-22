package com.example.selliaapp.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.selliaapp.auth.TenantProvider
import com.example.selliaapp.data.remote.TenantConfigContract
import com.example.selliaapp.di.AppModule
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class StorePalette(
    val primary: String = "",
    val secondary: String = "",
    val tertiary: String = ""
)

data class MarketingSettings(
    val publicStoreUrl: String = "",
    val storeName: String = "Tu tienda",
    val storeLogoUrl: String = "",
    val storePhone: String = "",
    val storeWhatsapp: String = "",
    val storeEmail: String = "",
    val tenantPalette: StorePalette = StorePalette(),
    val defaultPalette: StorePalette = StorePalette()
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
        val tenantPalettePrimary = stringPreferencesKey("tenant_palette_primary")
        val tenantPaletteSecondary = stringPreferencesKey("tenant_palette_secondary")
        val tenantPaletteTertiary = stringPreferencesKey("tenant_palette_tertiary")
        val defaultPalettePrimary = stringPreferencesKey("default_palette_primary")
        val defaultPaletteSecondary = stringPreferencesKey("default_palette_secondary")
        val defaultPaletteTertiary = stringPreferencesKey("default_palette_tertiary")
    }

    val settings: Flow<MarketingSettings> = dataStore.data.map { prefs ->
        MarketingSettings(
            publicStoreUrl = prefs[Keys.publicStoreUrl] ?: "",
            storeName = prefs[Keys.storeName] ?: "Tu tienda",
            storeLogoUrl = prefs[Keys.storeLogoUrl] ?: "",
            storePhone = prefs[Keys.storePhone] ?: "",
            storeWhatsapp = prefs[Keys.storeWhatsapp] ?: "",
            storeEmail = prefs[Keys.storeEmail] ?: "",
            tenantPalette = StorePalette(
                primary = prefs[Keys.tenantPalettePrimary] ?: "",
                secondary = prefs[Keys.tenantPaletteSecondary] ?: "",
                tertiary = prefs[Keys.tenantPaletteTertiary] ?: ""
            ),
            defaultPalette = StorePalette(
                primary = prefs[Keys.defaultPalettePrimary] ?: "",
                secondary = prefs[Keys.defaultPaletteSecondary] ?: "",
                tertiary = prefs[Keys.defaultPaletteTertiary] ?: ""
            )
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
            val palette = data["palette"] as? Map<*, *> ?: emptyMap<String, Any>()
            val defaultPalette = data["defaultPalette"] as? Map<*, *> ?: emptyMap<String, Any>()
            val cloudSettings = MarketingSettings(
                publicStoreUrl = (data["publicStoreUrl"] as? String).orEmpty(),
                storeName = (data["storeName"] as? String).orEmpty().ifBlank { "Tu tienda" },
                storeLogoUrl = (data["storeLogoUrl"] as? String).orEmpty(),
                storePhone = (data["storePhone"] as? String).orEmpty(),
                storeWhatsapp = (data["storeWhatsapp"] as? String).orEmpty(),
                storeEmail = (data["storeEmail"] as? String).orEmpty(),
                tenantPalette = StorePalette(
                    primary = normalizeHexColor((palette["primary"] as? String).orEmpty()),
                    secondary = normalizeHexColor((palette["secondary"] as? String).orEmpty()),
                    tertiary = normalizeHexColor((palette["tertiary"] as? String).orEmpty())
                ),
                defaultPalette = StorePalette(
                    primary = normalizeHexColor((defaultPalette["primary"] as? String).orEmpty()),
                    secondary = normalizeHexColor((defaultPalette["secondary"] as? String).orEmpty()),
                    tertiary = normalizeHexColor((defaultPalette["tertiary"] as? String).orEmpty())
                )
            )
            dataStore.edit { prefs ->
                prefs[Keys.publicStoreUrl] = normalizePublicStoreUrl(cloudSettings.publicStoreUrl)
                prefs[Keys.storeName] = cloudSettings.storeName
                prefs[Keys.storeLogoUrl] = cloudSettings.storeLogoUrl
                prefs[Keys.storePhone] = cloudSettings.storePhone
                prefs[Keys.storeWhatsapp] = cloudSettings.storeWhatsapp
                prefs[Keys.storeEmail] = cloudSettings.storeEmail
                prefs[Keys.tenantPalettePrimary] = cloudSettings.tenantPalette.primary
                prefs[Keys.tenantPaletteSecondary] = cloudSettings.tenantPalette.secondary
                prefs[Keys.tenantPaletteTertiary] = cloudSettings.tenantPalette.tertiary
                prefs[Keys.defaultPalettePrimary] = cloudSettings.defaultPalette.primary
                prefs[Keys.defaultPaletteSecondary] = cloudSettings.defaultPalette.secondary
                prefs[Keys.defaultPaletteTertiary] = cloudSettings.defaultPalette.tertiary
            }
        }
    }

    suspend fun updateSettings(updated: MarketingSettings) {
        val normalizedSettings = updated.copy(
            publicStoreUrl = normalizePublicStoreUrl(updated.publicStoreUrl),
            tenantPalette = updated.tenantPalette.normalized()
        )

        syncPublicStoreConfig(normalizedSettings)

        dataStore.edit { prefs ->
            prefs[Keys.publicStoreUrl] = normalizedSettings.publicStoreUrl
            prefs[Keys.storeName] = normalizedSettings.storeName
            prefs[Keys.storeLogoUrl] = normalizedSettings.storeLogoUrl
            prefs[Keys.storePhone] = normalizedSettings.storePhone
            prefs[Keys.storeWhatsapp] = normalizedSettings.storeWhatsapp
            prefs[Keys.storeEmail] = normalizedSettings.storeEmail
            prefs[Keys.tenantPalettePrimary] = normalizedSettings.tenantPalette.primary
            prefs[Keys.tenantPaletteSecondary] = normalizedSettings.tenantPalette.secondary
            prefs[Keys.tenantPaletteTertiary] = normalizedSettings.tenantPalette.tertiary
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
                    "storeEmail" to settings.storeEmail,
                    "palette" to mapOf(
                        "primary" to settings.tenantPalette.primary,
                        "secondary" to settings.tenantPalette.secondary,
                        "tertiary" to settings.tenantPalette.tertiary
                    )
                )
            )

            firestore.collection(TenantConfigContract.COLLECTION_TENANTS)
                .document(tenantId)
                .collection(TenantConfigContract.COLLECTION_CONFIG)
                .document(TenantConfigContract.DOC_MARKETING)
                .set(updatePayload, SetOptions.merge())
                .await()

            val directoryUpdate = mapOf(
                "publicStoreUrl" to publicStoreUrl,
                "publicDomain" to publicDomain,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            firestore.collection("tenant_directory")
                .document(tenantId)
                .set(directoryUpdate, SetOptions.merge())
                .await()

            firestore.collection("public_tenant_directory")
                .document(tenantId)
                .set(
                    mapOf(
                        "id" to tenantId,
                        "name" to settings.storeName,
                        "publicStoreUrl" to publicStoreUrl,
                        "publicDomain" to publicDomain,
                        "storeLogoUrl" to settings.storeLogoUrl,
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

    private fun StorePalette.normalized(): StorePalette = StorePalette(
        primary = normalizeHexColor(primary),
        secondary = normalizeHexColor(secondary),
        tertiary = normalizeHexColor(tertiary)
    )

    private fun normalizeHexColor(value: String): String {
        val trimmed = value.trim().uppercase()
        if (trimmed.isBlank()) return ""
        val withHash = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
        val isRgb = withHash.matches(Regex("^#[0-9A-F]{6}$"))
        return if (isRgb) withHash else ""
    }
}
