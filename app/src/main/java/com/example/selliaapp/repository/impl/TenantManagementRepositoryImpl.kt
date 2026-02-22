package com.example.selliaapp.repository.impl

import com.example.selliaapp.auth.TenantProvider
import com.example.selliaapp.di.AppModule
import com.example.selliaapp.repository.TenantManagementRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TenantManagementRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val tenantProvider: TenantProvider,
    @AppModule.IoDispatcher private val io: CoroutineDispatcher
) : TenantManagementRepository {

    override suspend fun requestTenantDeactivation(): Result<Unit> = withContext(io) {
        runCatching {
            val tenantId = tenantProvider.requireTenantId()
            firestore.collection("tenants")
                .document(tenantId)
                .set(
                    mapOf(
                        "status" to "disabled",
                        "deactivationRequestedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .await()
            Unit
        }
    }

    override suspend fun requestTenantReactivation(): Result<Unit> = withContext(io) {
        runCatching {
            val tenantId = tenantProvider.requireTenantId()
            firestore.collection("tenants")
                .document(tenantId)
                .set(
                    mapOf(
                        "status" to "reactivation_requested",
                        "reactivationRequestedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .await()
            Unit
        }
    }

    override suspend fun deleteTenantWithDoubleCheck(confirmTenantId: String, confirmPhrase: String): Result<Unit> = withContext(io) {
        runCatching {
            val tenantId = tenantProvider.requireTenantId()
            if (confirmTenantId.trim() != tenantId) {
                throw IllegalArgumentException("El tenant ID ingresado no coincide")
            }
            if (confirmPhrase.trim().uppercase() != "ELIMINAR") {
                throw IllegalArgumentException("Debés escribir ELIMINAR para confirmar")
            }
            firestore.collection("tenants").document(tenantId).delete().await()
            firestore.collection("tenant_directory").document(tenantId).delete().await()
            firestore.collection("public_tenant_directory").document(tenantId).delete().await()
            Unit
        }
    }
}
