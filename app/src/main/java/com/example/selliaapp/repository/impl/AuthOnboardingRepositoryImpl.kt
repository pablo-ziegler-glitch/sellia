package com.example.selliaapp.repository.impl

import com.example.selliaapp.di.AppModule
import com.example.selliaapp.domain.security.AppRole
import com.example.selliaapp.repository.AuthOnboardingRepository
import com.example.selliaapp.repository.OnboardingResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthOnboardingRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    @AppModule.IoDispatcher private val io: CoroutineDispatcher
) : AuthOnboardingRepository {

    private companion object {
        const val ACCOUNT_TYPE_STORE_OWNER = "store_owner"
        const val ACCOUNT_ORIGIN_ADMIN_FLOW = "admin_flow"
        const val TENANT_ACTIVATION_MODE_AUTO = "auto"
        const val TENANT_ACTIVATION_MODE_MANUAL = "manual"
    }

    override suspend fun registerStore(
        email: String,
        password: String,
        storeName: String,
        storeAddress: String,
        storePhone: String,
        skuPrefix: String?
    ): Result<OnboardingResult> = withContext(io) {
        runCatching {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw IllegalStateException("No se pudo crear el usuario")
            val tenantId = UUID.randomUUID().toString()
            val batch = firestore.batch()
            val createdAt = FieldValue.serverTimestamp()
            val resolvedSkuPrefix = normalizeSkuPrefix(skuPrefix) ?: deriveSkuPrefix(storeName)
            val activationMode = resolveTenantActivationMode()
            val requiresManualApproval = activationMode == TENANT_ACTIVATION_MODE_MANUAL
            val accountStatus = if (requiresManualApproval) "pending" else "active"
            val tenantStatus = if (requiresManualApproval) "pending_approval" else "active"
            val activationPolicy = if (requiresManualApproval) "manual_admin_approval" else "auto_active"
            val loginEnabled = !requiresManualApproval

            val userRef = firestore.collection("users").document(user.uid)
            batch.set(
                userRef,
                mapOf(
                    "tenantId" to tenantId,
                    "tenantIds" to listOf(tenantId),
                    "email" to email,
                    "role" to AppRole.OWNER.raw,
                    "accountType" to ACCOUNT_TYPE_STORE_OWNER,
                    "status" to accountStatus,
                    "isAdmin" to false,
                    "isSuperAdmin" to false,
                    "createdAt" to createdAt,
                    "accountOrigin" to ACCOUNT_ORIGIN_ADMIN_FLOW,
                    "isActive" to loginEnabled
                )
            )

            val tenantRef = firestore.collection("tenants").document(tenantId)
            batch.set(
                tenantRef,
                mapOf(
                    "id" to tenantId,
                    "name" to storeName,
                    "address" to storeAddress,
                    "phone" to storePhone,
                    "ownerUid" to user.uid,
                    "ownerEmail" to email,
                    "status" to tenantStatus,
                    "activationPolicy" to activationPolicy,
                    "loginEnabled" to loginEnabled,
                    "isActive" to loginEnabled,
                    "enabledModules" to defaultEnabledModules(),
                    "skuPrefix" to resolvedSkuPrefix,
                    "createdAt" to createdAt,
                    "requestOrigin" to ACCOUNT_ORIGIN_ADMIN_FLOW
                )
            )

            val directoryRef = firestore.collection("tenant_directory").document(tenantId)
            batch.set(
                directoryRef,
                mapOf(
                    "id" to tenantId,
                    "name" to storeName,
                    "ownerUid" to user.uid,
                    "skuPrefix" to resolvedSkuPrefix,
                    "createdAt" to createdAt
                )
            )

            val publicDirectoryRef = firestore.collection("public_tenant_directory").document(tenantId)
            batch.set(
                publicDirectoryRef,
                mapOf(
                    "id" to tenantId,
                    "name" to storeName,
                    "createdAt" to createdAt,
                    "updatedAt" to createdAt
                )
            )

            val requestRef = firestore.collection("account_requests").document(user.uid)
            batch.set(
                requestRef,
                mapOf(
                    "uid" to user.uid,
                    "requestedBy" to user.uid,
                    "email" to email,
                    "accountType" to ACCOUNT_TYPE_STORE_OWNER,
                    "status" to accountStatus,
                    "activationPolicy" to activationPolicy,
                    "loginEnabled" to loginEnabled,
                    "isActive" to loginEnabled,
                    "tenantId" to tenantId,
                    "storeName" to storeName,
                    "storeAddress" to storeAddress,
                    "storePhone" to storePhone,
                    "enabledModules" to defaultEnabledModules(),
                    "skuPrefix" to resolvedSkuPrefix,
                    "createdAt" to createdAt,
                    "requestOrigin" to ACCOUNT_ORIGIN_ADMIN_FLOW
                )
            )

            val tenantUserRef = firestore.collection("tenant_users")
                .document("${tenantId}_${email.trim().lowercase()}")
            batch.set(
                tenantUserRef,
                mapOf(
                    "tenantId" to tenantId,
                    "tenantIds" to listOf(tenantId),
                    "name" to storeName,
                    "email" to email.trim().lowercase(),
                    "role" to AppRole.OWNER.raw,
                    "isActive" to loginEnabled,
                    "updatedAt" to createdAt,
                    "provisioningFlow" to ACCOUNT_ORIGIN_ADMIN_FLOW
                ),
                SetOptions.merge()
            )

            batch.commit().await()
            sendEmailVerification(user)
            OnboardingResult(uid = user.uid, tenantId = tenantId)
        }.onFailure {
            val currentUser = auth.currentUser
            if (currentUser != null && currentUser.email == email) {
                runCatching { currentUser.delete().await() }
            }
        }
    }

    private suspend fun sendEmailVerification(user: FirebaseUser) {
        user.sendEmailVerification().await()
    }


    private fun normalizeSkuPrefix(raw: String?): String? {
        val normalized = raw?.trim()?.uppercase()?.replace("[^A-Z0-9]".toRegex(), "")?.take(6).orEmpty()
        return normalized.takeIf { it.length >= 3 }
    }

    private fun deriveSkuPrefix(storeName: String): String {
        val normalized = storeName.uppercase().replace("[^A-Z0-9]".toRegex(), "")
        return normalized.take(3).padEnd(3, 'X')
    }

    private fun defaultEnabledModules(): Map<String, Boolean> = mapOf(
        "catalog" to true,
        "sales" to true,
        "stock" to true,
        "reports" to true,
        "cash" to true,
        "marketing" to false
    )

    private suspend fun resolveTenantActivationMode(): String {
        return runCatching {
            val result = functions
                .getHttpsCallable("getTenantOnboardingPolicy")
                .call()
                .await()
            val data = result.data as? Map<*, *> ?: emptyMap<Any, Any>()
            val mode = (data["tenantActivationMode"] as? String)?.trim()?.lowercase().orEmpty()
            when (mode) {
                TENANT_ACTIVATION_MODE_MANUAL -> TENANT_ACTIVATION_MODE_MANUAL
                else -> TENANT_ACTIVATION_MODE_AUTO
            }
        }.getOrElse {
            TENANT_ACTIVATION_MODE_AUTO
        }
    }
}
