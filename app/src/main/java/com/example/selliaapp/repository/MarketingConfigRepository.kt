package com.example.selliaapp.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class MarketingSettings(
    val publicStoreUrl: String = "",
    val storeName: String = "Tu tienda",
    val storeLogoUrl: String = "",
    val storePhone: String = "",
    val storeWhatsapp: String = "",
    val storeEmail: String = ""
)

class MarketingConfigRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
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
        dataStore.edit { prefs ->
            prefs[Keys.publicStoreUrl] = updated.publicStoreUrl
            prefs[Keys.storeName] = updated.storeName
            prefs[Keys.storeLogoUrl] = updated.storeLogoUrl
            prefs[Keys.storePhone] = updated.storePhone
            prefs[Keys.storeWhatsapp] = updated.storeWhatsapp
            prefs[Keys.storeEmail] = updated.storeEmail
        }
    }
}
