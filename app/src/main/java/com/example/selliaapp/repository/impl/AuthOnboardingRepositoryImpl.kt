package com.example.selliaapp.repository.impl

import com.example.selliaapp.di.AppModule
import com.example.selliaapp.domain.security.AppRole
import com.example.selliaapp.repository.AuthOnboardingRepository
import com.example.selliaapp.repository.OnboardingResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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

    override suspend fun registerStore(
        email: String,
        password: String,
        storeName: String
    ): Result<OnboardingResult> = withContext(io) {
        runCatching {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw IllegalStateException("No se pudo crear el usuario")
            val tenantId = UUID.randomUUID().toString()
            val batch = firestore.batch()
            val createdAt = FieldValue.serverTimestamp()

            val userRef = firestore.collection("users").document(user.uid)
            batch.set(
                userRef,
                mapOf(
                    "tenantId" to tenantId,
                    "email" to email,
                    "role" to AppRole.OWNER.raw,
                    "createdAt" to createdAt
                )
            )

            val tenantRef = firestore.collection("tenants").document(tenantId)
            batch.set(
                tenantRef,
                mapOf(
                    "id" to tenantId,
                    "name" to storeName,
                    "ownerUid" to user.uid,
                    "ownerEmail" to email,
                    "createdAt" to createdAt
                )
            )

            val directoryRef = firestore.collection("tenant_directory").document(tenantId)
            batch.set(
                directoryRef,
                mapOf(
                    "id" to tenantId,
                    "name" to storeName,
                    "ownerUid" to user.uid,
                    "createdAt" to createdAt
                )
            )

            batch.commit().await()
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
        tenantId: String
    ): Result<OnboardingResult> = withContext(io) {
        runCatching {
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
                    "createdAt" to createdAt
                )
            ).await()
            OnboardingResult(uid = user.uid, tenantId = tenantId)
        }.onFailure {
            val currentUser = auth.currentUser
            if (currentUser != null && currentUser.email == email) {
                runCatching { currentUser.delete().await() }
            }
        }
    }
}
