package com.example.selliaapp.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.selliaapp.domain.security.SecurityHashing
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class SecuritySettings(
    val adminEmailHash: String = SecurityConfigDefaults.ADMIN_EMAIL_HASH
)

object SecurityConfigDefaults {
    const val ADMIN_EMAIL_HASH =
        "50a912031f5941fb710653ed4025fcac0ceed9887c181b5847288ce5e6cee99d"
    const val DEFAULT_PASSWORD_HASH =
        "15e2b0d3c33891ebb0f1ef609ec419420c20e320ce94c65fbc8c3312448eb225"
}

class SecurityConfigRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val adminEmailHash = stringPreferencesKey("admin_email_hash")
    }

    val settings: Flow<SecuritySettings> = dataStore.data.map { prefs ->
        SecuritySettings(
            adminEmailHash = prefs[Keys.adminEmailHash] ?: SecurityConfigDefaults.ADMIN_EMAIL_HASH
        )
    }

    suspend fun updateAdminEmail(email: String) {
        val hash = SecurityHashing.hashEmail(email)
        dataStore.edit { prefs ->
            prefs[Keys.adminEmailHash] = hash
        }
    }

    suspend fun getAdminEmailHash(): String = settings.first().adminEmailHash
}
