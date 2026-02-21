package com.example.selliaapp.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.selliaapp.auth.TenantProvider
import com.example.selliaapp.data.remote.TenantConfigContract
import com.example.selliaapp.di.AppModule
import com.example.selliaapp.domain.security.SecurityHashing
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

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
    private val dataStore: DataStore<Preferences>,
    private val firestore: FirebaseFirestore,
    private val tenantProvider: TenantProvider,
    @AppModule.IoDispatcher private val io: CoroutineDispatcher
) {
    private object Keys {
        val adminEmailHash = stringPreferencesKey("admin_email_hash")
    }

    val settings: Flow<SecuritySettings> = dataStore.data.map { prefs ->
        SecuritySettings(
            adminEmailHash = prefs[Keys.adminEmailHash] ?: SecurityConfigDefaults.ADMIN_EMAIL_HASH
        )
    }

    suspend fun refreshFromCloud() = withContext(io) {
        runCatching {
            val tenantId = tenantProvider.requireTenantId()
            val snapshot = firestore.collection(TenantConfigContract.COLLECTION_TENANTS)
                .document(tenantId)
                .collection(TenantConfigContract.COLLECTION_CONFIG)
                .document(TenantConfigContract.DOC_SECURITY)
                .get()
                .await()
            val data = snapshot.get(TenantConfigContract.Fields.DATA) as? Map<*, *> ?: return@runCatching
            val hash = (data["adminEmailHash"] as? String).orEmpty().ifBlank { SecurityConfigDefaults.ADMIN_EMAIL_HASH }
            dataStore.edit { prefs ->
                prefs[Keys.adminEmailHash] = hash
            }
        }
    }

    suspend fun updateAdminEmail(email: String) {
        val hash = SecurityHashing.hashEmail(email)

        withContext(io) {
            runCatching {
                val tenantId = tenantProvider.requireTenantId()
                val payload = mapOf(
                    TenantConfigContract.Fields.SCHEMA_VERSION to TenantConfigContract.CURRENT_SCHEMA_VERSION,
                    TenantConfigContract.Fields.UPDATED_AT to FieldValue.serverTimestamp(),
                    TenantConfigContract.Fields.UPDATED_BY to "android_security",
                    TenantConfigContract.Fields.AUDIT to mapOf(
                        "event" to "UPSERT_SECURITY_CONFIG",
                        "at" to FieldValue.serverTimestamp(),
                        "by" to "android_security"
                    ),
                    TenantConfigContract.Fields.DATA to mapOf(
                        "adminEmailHash" to hash
                    )
                )
                firestore.collection(TenantConfigContract.COLLECTION_TENANTS)
                    .document(tenantId)
                    .collection(TenantConfigContract.COLLECTION_CONFIG)
                    .document(TenantConfigContract.DOC_SECURITY)
                    .set(payload, SetOptions.merge())
                    .await()
            }
        }

        dataStore.edit { prefs ->
            prefs[Keys.adminEmailHash] = hash
        }
    }

    suspend fun getAdminEmailHash(): String = settings.first().adminEmailHash
}
