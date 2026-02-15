package com.example.selliaapp.repository.impl

import com.example.selliaapp.data.model.onboarding.AccountRequest
import com.example.selliaapp.data.model.onboarding.AccountRequestStatus
import com.example.selliaapp.data.model.onboarding.AccountRequestType
import com.example.selliaapp.di.AppModule
import com.example.selliaapp.repository.AccountRequestsRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRequestsRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    @AppModule.IoDispatcher private val io: CoroutineDispatcher
) : AccountRequestsRepository {

    override suspend fun fetchRequests(): Result<List<AccountRequest>> = withContext(io) {
        runCatching {
            val snapshot = firestore.collection("account_requests")
                .get()
                .await()
            snapshot.documents
                .sortedByDescending { doc ->
                    doc.getTimestamp("createdAt")?.toDate()?.time
                        ?: doc.getTimestamp("updatedAt")?.toDate()?.time
                        ?: 0L
                }
                .mapNotNull { doc ->
                val email = doc.getString("email")?.trim().orEmpty()
                if (email.isBlank()) return@mapNotNull null
                val accountType = AccountRequestType.fromRaw(doc.getString("accountType"))
                val status = AccountRequestStatus.fromRaw(doc.getString("status"))
                @Suppress("UNCHECKED_CAST")
                val modules = doc.get("enabledModules") as? Map<String, Boolean> ?: emptyMap()
                AccountRequest(
                    id = doc.id,
                    email = email,
                    accountType = accountType,
                    status = status,
                    tenantId = doc.getString("tenantId"),
                    tenantName = doc.getString("tenantName"),
                    storeName = doc.getString("storeName"),
                    storeAddress = doc.getString("storeAddress"),
                    storePhone = doc.getString("storePhone"),
                    contactName = doc.getString("contactName"),
                    contactPhone = doc.getString("contactPhone"),
                    enabledModules = modules
                )
            }
        }
    }

    override suspend fun updateRequest(
        requestId: String,
        status: AccountRequestStatus,
        enabledModules: Map<String, Boolean>
    ): Result<Unit> = withContext(io) {
        runCatching {
            val requestRef = firestore.collection("account_requests").document(requestId)
            val requestSnapshot = requestRef.get().await()
            if (!requestSnapshot.exists()) {
                throw IllegalArgumentException("La solicitud ya no existe.")
            }
            val tenantId = requestSnapshot.getString("tenantId")
            val accountType = AccountRequestType.fromRaw(requestSnapshot.getString("accountType"))
            val isApproval = status == AccountRequestStatus.ACTIVE
            val resolvedStatus = if (isApproval) AccountRequestStatus.ACTIVE.raw else status.raw
            val loginEnabled = isApproval

            val updatedAt = FieldValue.serverTimestamp()
            val writeBatch = firestore.batch()
            val updates = mapOf(
                "status" to resolvedStatus,
                "loginEnabled" to loginEnabled,
                "enabledModules" to enabledModules,
                "updatedAt" to updatedAt
            )
            writeBatch.set(requestRef, updates, SetOptions.merge())

            val userRef = firestore.collection("users").document(requestId)
            writeBatch.set(
                userRef,
                mapOf(
                    "status" to resolvedStatus,
                    "activationPolicy" to "manual_admin_approval",
                    "loginEnabled" to loginEnabled,
                    "enabledModules" to enabledModules,
                    "updatedAt" to updatedAt
                ),
                SetOptions.merge()
            )

            if (!tenantId.isNullOrBlank() && (accountType == AccountRequestType.STORE_OWNER || isApproval)) {
                val tenantRef = firestore.collection("tenants").document(tenantId)
                writeBatch.set(
                    tenantRef,
                    mapOf(
                        "status" to resolvedStatus,
                        "activationPolicy" to "manual_admin_approval",
                        "loginEnabled" to loginEnabled,
                        "enabledModules" to enabledModules,
                        "updatedAt" to updatedAt
                    ),
                    SetOptions.merge()
                )
            }

            if (
                accountType == AccountRequestType.STORE_OWNER
                && isApproval
                && !tenantId.isNullOrBlank()
            ) {
                val ownerEmail = requestSnapshot.getString("email")?.trim()?.lowercase().orEmpty()
                if (ownerEmail.isNotBlank()) {
                    val ownerName = requestSnapshot.getString("storeName")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: ownerEmail.substringBefore("@")
                    val tenantOwnerRef = firestore.collection("tenant_users")
                        .document("${tenantId}_${ownerEmail}")
                    writeBatch.set(
                        tenantOwnerRef,
                        mapOf(
                            "tenantId" to tenantId,
                            "name" to ownerName,
                            "email" to ownerEmail,
                            "role" to "owner",
                            "isActive" to true,
                            "updatedAt" to updatedAt
                        ),
                        SetOptions.merge()
                    )
                }
            }

            writeBatch.commit().await()
            Unit
        }
    }
}
