package com.example.selliaapp.repository.impl

import com.example.selliaapp.data.model.onboarding.AccountRequest
import com.example.selliaapp.data.model.onboarding.AccountRequestStatus
import com.example.selliaapp.data.model.onboarding.AccountRequestType
import com.example.selliaapp.di.AppModule
import com.example.selliaapp.repository.AccountRequestsRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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
                .orderBy("createdAt")
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
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
            val updates = mapOf(
                "status" to status.raw,
                "enabledModules" to enabledModules,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            requestRef.set(updates, com.google.firebase.firestore.SetOptions.merge()).await()

            val userRef = firestore.collection("users").document(requestId)
            userRef.set(
                mapOf(
                    "status" to status.raw,
                    "enabledModules" to enabledModules
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()

            if (accountType == AccountRequestType.STORE_OWNER && !tenantId.isNullOrBlank()) {
                firestore.collection("tenants").document(tenantId)
                    .set(
                        mapOf(
                            "status" to status.raw,
                            "enabledModules" to enabledModules
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    .await()
            }
        }
    }
}
