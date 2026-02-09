package com.example.selliaapp.repository.impl

import com.example.selliaapp.data.dao.UserDao
import com.example.selliaapp.di.AppModule.IoDispatcher
import com.example.selliaapp.domain.security.AppRole
import com.example.selliaapp.domain.security.RolePermissions
import com.example.selliaapp.domain.security.UserAccessState
import com.example.selliaapp.domain.security.SecurityHashing
import com.example.selliaapp.repository.AccessControlRepository
import com.example.selliaapp.repository.SecurityConfigRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessControlRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val securityConfigRepository: SecurityConfigRepository,
    @IoDispatcher private val io: CoroutineDispatcher
) : AccessControlRepository {

    override fun observeAccessState(): Flow<UserAccessState> = callbackFlow {
        val scope = CoroutineScope(SupervisorJob() + io)
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            scope.launch {
                trySend(resolveAccess(firebaseAuth.currentUser?.email))
            }
        }
        auth.addAuthStateListener(listener)
        scope.launch {
            trySend(resolveAccess(auth.currentUser?.email))
        }
        awaitClose {
            auth.removeAuthStateListener(listener)
            scope.cancel()
        }
    }

    override suspend fun getAccessState(): UserAccessState =
        resolveAccess(auth.currentUser?.email)

    private suspend fun resolveAccess(email: String?): UserAccessState = withContext(io) {
        val adminHash = securityConfigRepository.getAdminEmailHash()
        val isConfiguredAdmin = !email.isNullOrBlank() &&
            SecurityHashing.hashEmail(email) == adminHash
        val user = when {
            !email.isNullOrBlank() -> userDao.getByEmail(email)
            else -> userDao.getFirst()
        }
        val firestoreRole = resolveRoleFromCloud()
        val totalUsers = userDao.countUsers()
        val role = when {
            isConfiguredAdmin -> AppRole.SUPER_ADMIN
            user != null && user.isActive -> AppRole.fromRaw(user.role)
            firestoreRole != null -> firestoreRole
            totalUsers == 0 && !email.isNullOrBlank() -> AppRole.SUPER_ADMIN
            else -> AppRole.fromRaw(null)
        }
        UserAccessState(
            email = user?.email ?: email,
            role = role,
            permissions = RolePermissions.forRole(role)
        )
    }

    private suspend fun resolveRoleFromCloud(): AppRole? {
        val uid = auth.currentUser?.uid ?: return null
        val snapshot = firestore.collection("users").document(uid).get().await()
        if (!snapshot.exists()) return null
        val status = snapshot.getString("status")?.lowercase()
        if (!status.isNullOrBlank() && status != "active") {
            return AppRole.VIEWER
        }
        val roleRaw = snapshot.getString("role")?.trim()?.lowercase()
        val isSuperAdmin = snapshot.getBoolean("isSuperAdmin") == true
        val isAdmin = snapshot.getBoolean("isAdmin") == true
        return when {
            isSuperAdmin -> AppRole.SUPER_ADMIN
            roleRaw == AppRole.SUPER_ADMIN.raw || isAdmin -> AppRole.ADMIN
            else -> AppRole.fromRaw(roleRaw)
        }
    }
}
