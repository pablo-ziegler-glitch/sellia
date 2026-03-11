package com.example.selliaapp.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

data class PublicCatalogSettings(
    val catalogEnabled: Boolean = true,
    val showPrices: Boolean = true,
    val showCashPrice: Boolean = true,
    val heroTitle: String = "",
    val heroSubtitle: String = "",
    val footerText: String = "",
    val defaultSort: String = "name_asc"
)

class PublicCatalogConfigRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val firestore: FirebaseFirestore,
    private val tenantProvider: TenantProvider,
    @AppModule.IoDispatcher private val io: CoroutineDispatcher
) {
    private object Keys {
        val catalogEnabled = booleanPreferencesKey("public_catalog_enabled")
        val showPrices = booleanPreferencesKey("public_catalog_show_prices")
        val showCashPrice = booleanPreferencesKey("public_catalog_show_cash_price")
        val heroTitle = stringPreferencesKey("public_catalog_hero_title")
        val heroSubtitle = stringPreferencesKey("public_catalog_hero_subtitle")
        val footerText = stringPreferencesKey("public_catalog_footer_text")
        val defaultSort = stringPreferencesKey("public_catalog_default_sort")
    }

    val settings: Flow<PublicCatalogSettings> = dataStore.data.map { prefs ->
        PublicCatalogSettings(
            catalogEnabled = prefs[Keys.catalogEnabled] ?: true,
            showPrices = prefs[Keys.showPrices] ?: true,
            showCashPrice = prefs[Keys.showCashPrice] ?: true,
            heroTitle = prefs[Keys.heroTitle] ?: "",
            heroSubtitle = prefs[Keys.heroSubtitle] ?: "",
            footerText = prefs[Keys.footerText] ?: "",
            defaultSort = prefs[Keys.defaultSort] ?: "name_asc"
        )
    }

    suspend fun refreshFromCloud() = withContext(io) {
        runCatching {
            val tenantId = tenantProvider.requireTenantId()
            val snapshot = firestore.collection(TenantConfigContract.COLLECTION_TENANTS)
                .document(tenantId)
                .collection(TenantConfigContract.COLLECTION_CONFIG)
                .document(TenantConfigContract.DOC_PUBLIC_CATALOG)
                .get()
                .await()
            val data = snapshot.get(TenantConfigContract.Fields.DATA) as? Map<*, *> ?: return@runCatching
            val cloudSettings = PublicCatalogSettings(
                catalogEnabled = (data["catalogEnabled"] as? Boolean) ?: true,
                showPrices = (data["showPrices"] as? Boolean) ?: true,
                showCashPrice = (data["showCashPrice"] as? Boolean) ?: true,
                heroTitle = (data["heroTitle"] as? String).orEmpty(),
                heroSubtitle = (data["heroSubtitle"] as? String).orEmpty(),
                footerText = (data["footerText"] as? String).orEmpty(),
                defaultSort = (data["defaultSort"] as? String).orEmpty().ifBlank { "name_asc" }
            )
            dataStore.edit { prefs ->
                prefs[Keys.catalogEnabled] = cloudSettings.catalogEnabled
                prefs[Keys.showPrices] = cloudSettings.showPrices
                prefs[Keys.showCashPrice] = cloudSettings.showCashPrice
                prefs[Keys.heroTitle] = cloudSettings.heroTitle
                prefs[Keys.heroSubtitle] = cloudSettings.heroSubtitle
                prefs[Keys.footerText] = cloudSettings.footerText
                prefs[Keys.defaultSort] = cloudSettings.defaultSort
            }
        }
    }

    suspend fun updateSettings(updated: PublicCatalogSettings) = withContext(io) {
        val tenantId = tenantProvider.requireTenantId()
        val payload = mapOf(
            TenantConfigContract.Fields.SCHEMA_VERSION to TenantConfigContract.CURRENT_SCHEMA_VERSION,
            TenantConfigContract.Fields.UPDATED_AT to FieldValue.serverTimestamp(),
            TenantConfigContract.Fields.UPDATED_BY to "android_public_catalog",
            TenantConfigContract.Fields.AUDIT to mapOf(
                "event" to "UPSERT_PUBLIC_CATALOG",
                "at" to FieldValue.serverTimestamp(),
                "by" to "android_public_catalog"
            ),
            TenantConfigContract.Fields.DATA to mapOf(
                "catalogEnabled" to updated.catalogEnabled,
                "showPrices" to updated.showPrices,
                "showCashPrice" to updated.showCashPrice,
                "heroTitle" to updated.heroTitle.trim(),
                "heroSubtitle" to updated.heroSubtitle.trim(),
                "footerText" to updated.footerText.trim(),
                "defaultSort" to updated.defaultSort
            )
        )
        firestore.collection(TenantConfigContract.COLLECTION_TENANTS)
            .document(tenantId)
            .collection(TenantConfigContract.COLLECTION_CONFIG)
            .document(TenantConfigContract.DOC_PUBLIC_CATALOG)
            .set(payload, SetOptions.merge())
            .await()

        dataStore.edit { prefs ->
            prefs[Keys.catalogEnabled] = updated.catalogEnabled
            prefs[Keys.showPrices] = updated.showPrices
            prefs[Keys.showCashPrice] = updated.showCashPrice
            prefs[Keys.heroTitle] = updated.heroTitle.trim()
            prefs[Keys.heroSubtitle] = updated.heroSubtitle.trim()
            prefs[Keys.footerText] = updated.footerText.trim()
            prefs[Keys.defaultSort] = updated.defaultSort
        }
    }
}
