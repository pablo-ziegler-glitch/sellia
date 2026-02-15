package com.example.selliaapp.repository.impl

import com.example.selliaapp.di.AppModule
import com.example.selliaapp.domain.security.AppRole
import com.example.selliaapp.repository.AuthOnboardingRepository
import com.example.selliaapp.repository.OnboardingResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
    @AppModule.IoDispatcher private val io: CoroutineDispatcher
) : AuthOnboardingRepository {

    private companion object {
        const val ACCOUNT_TYPE_STORE_OWNER = "store_owner"
        const val ACCOUNT_TYPE_FINAL_CUSTOMER = "final_customer"
        const val ACCOUNT_ORIGIN_PUBLIC_SIGN_UP = "public_sign_up"
        const val ACCOUNT_ORIGIN_ADMIN_FLOW = "admin_flow"
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

            val userRef = firestore.collection("users").document(user.uid)
            batch.set(
                userRef,
                mapOf(
                    "tenantId" to tenantId,
                    "email" to email,
                    "role" to AppRole.OWNER.raw,
                    "accountType" to ACCOUNT_TYPE_STORE_OWNER,
                    "status" to "pending",
                    "createdAt" to createdAt,
                    "accountOrigin" to ACCOUNT_ORIGIN_ADMIN_FLOW
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
                    "status" to "pending",
                    "activationPolicy" to "manual_admin_approval",
                    "loginEnabled" to false,
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

            val requestRef = firestore.collection("account_requests").document(user.uid)
            batch.set(
                requestRef,
                mapOf(
                    "uid" to user.uid,
                    "email" to email,
                    "accountType" to ACCOUNT_TYPE_STORE_OWNER,
                    "status" to "pending",
                    "activationPolicy" to "manual_admin_approval",
                    "loginEnabled" to false,
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
                    "name" to storeName,
                    "email" to email.trim().lowercase(),
                    "role" to AppRole.OWNER.raw,
                    "isActive" to true,
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

    override suspend fun registerViewer(
        email: String,
        password: String,
        tenantId: String?,
        tenantName: String?,
        customerName: String,
        customerPhone: String?
    ): Result<OnboardingResult> = withContext(io) {
        runCatching {
            // El alta pública siempre debe crear un cliente final (viewer).
            val tenantSnapshot = firestore.collection("tenants")
                .document(tenantId)
                .get()
                .await()
            if (!tenantSnapshot.exists()) {
                throw IllegalArgumentException("La tienda seleccionada no existe")
            }
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw IllegalStateException("No se pudo crear el usuario")
            val createdAt = FieldValue.serverTimestamp()
            val userRef = firestore.collection("users").document(user.uid)
            userRef.set(
                mapOf(
                    "tenantId" to tenantId,
                    "email" to email,
                    "role" to AppRole.VIEWER.raw,
                    "accountType" to ACCOUNT_TYPE_FINAL_CUSTOMER,
                    "status" to "active",
                    "displayName" to customerName,
                    "phone" to customerPhone,
                    "createdAt" to createdAt,
                    "accountOrigin" to ACCOUNT_ORIGIN_PUBLIC_SIGN_UP
                )
            ).await()
            firestore.collection("tenant_users")
                .document("${tenantId}_${email.trim().lowercase()}")
                .set(
                    mapOf(
                        "tenantId" to tenantId,
                        "name" to customerName,
                        "email" to email.trim().lowercase(),
                        "role" to AppRole.VIEWER.raw,
                        "isActive" to true,
                        "updatedAt" to createdAt,
                        "provisioningFlow" to ACCOUNT_ORIGIN_PUBLIC_SIGN_UP
                    ),
                    SetOptions.merge()
                )
                .await()
            firestore.collection("account_requests")
                .document(user.uid)
                .set(
                    mapOf(
                        "uid" to user.uid,
                        "email" to email,
                        "accountType" to ACCOUNT_TYPE_FINAL_CUSTOMER,
                        "status" to "active",
                        "tenantId" to normalizedTenantId,
                        "tenantName" to tenantName.orEmpty(),
                        "contactName" to customerName,
                        "contactPhone" to customerPhone,
                        "createdAt" to createdAt,
                        "requestOrigin" to ACCOUNT_ORIGIN_PUBLIC_SIGN_UP
                    ),
                    SetOptions.merge()
                )
                .await()
            sendEmailVerificationSafely(user)
            OnboardingResult(uid = user.uid, tenantId = normalizedTenantId.ifBlank { "UNASSIGNED" })
        }.onFailure {
            val currentUser = auth.currentUser
            if (currentUser != null && currentUser.email == email) {
                runCatching { currentUser.delete().await() }
            }
        }
    }

    override suspend fun registerViewerWithGoogle(
        idToken: String,
        tenantId: String,
        tenantName: String
    ): Result<OnboardingResult> = withContext(io) {
        runCatching {
            // Google Sign-In público: restringido a cliente final (viewer).
            val tenantSnapshot = firestore.collection("tenants")
                .document(tenantId)
                .get()
                .await()
            if (!tenantSnapshot.exists()) {
                throw IllegalArgumentException("La tienda seleccionada no existe")
            }
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: throw IllegalStateException("No se pudo crear el usuario")
            val createdAt = FieldValue.serverTimestamp()
            val userRef = firestore.collection("users").document(user.uid)
            val normalizedEmail = (user.email ?: "").trim().lowercase()
            val displayName = (user.displayName ?: "").trim()
            userRef.set(
                mapOf(
                    "tenantId" to tenantId,
                    "email" to normalizedEmail,
                    "role" to AppRole.VIEWER.raw,
                    "accountType" to ACCOUNT_TYPE_FINAL_CUSTOMER,
                    "status" to "active",
                    "displayName" to displayName,
                    "createdAt" to createdAt,
                    "accountOrigin" to ACCOUNT_ORIGIN_PUBLIC_SIGN_UP
                ),
                SetOptions.merge()
            ).await()
            firestore.collection("tenant_users")
                .document("${tenantId}_${normalizedEmail}")
                .set(
                    mapOf(
                        "tenantId" to tenantId,
                        "name" to displayName,
                        "email" to normalizedEmail,
                        "role" to AppRole.VIEWER.raw,
                        "isActive" to true,
                        "updatedAt" to createdAt,
                        "provisioningFlow" to ACCOUNT_ORIGIN_PUBLIC_SIGN_UP
                    ),
                    SetOptions.merge()
                )
                .await()
            firestore.collection("account_requests")
                .document(user.uid)
                .set(
                    mapOf(
                        "uid" to user.uid,
                        "email" to (user.email ?: ""),
                        "accountType" to ACCOUNT_TYPE_FINAL_CUSTOMER,
                        "status" to "active",
                        "tenantId" to tenantId,
                        "tenantName" to tenantName,
                        "contactName" to (user.displayName ?: ""),
                        "createdAt" to createdAt,
                        "requestOrigin" to ACCOUNT_ORIGIN_PUBLIC_SIGN_UP
                    ),
                    SetOptions.merge()
                )
                .await()
            OnboardingResult(uid = user.uid, tenantId = tenantId)
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
}
