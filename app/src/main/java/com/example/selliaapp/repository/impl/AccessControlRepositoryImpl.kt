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
        val normalizedEmail = email?.trim()?.lowercase()
        val adminHash = securityConfigRepository.getAdminEmailHash()
        val isConfiguredAdmin = !normalizedEmail.isNullOrBlank() &&
            SecurityHashing.hashEmail(normalizedEmail) == adminHash
        val user = when {
            !email.isNullOrBlank() -> userDao.getByEmail(email)
            else -> userDao.getFirst()
        }
        val firestoreRole = resolveRoleFromCloud()
        val totalUsers = userDao.countUsers()
        val role = resolveEffectiveRole(
            isConfiguredAdmin = isConfiguredAdmin,
            localRole = user?.let { AppRole.fromRaw(it.role) },
            localUserIsActive = user?.isActive == true,
            firestoreRole = firestoreRole,
            totalUsers = totalUsers,
            hasAuthenticatedEmail = !email.isNullOrBlank()
        )
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
        val roleRaw = snapshot.getString("role")?.trim()?.lowercase()
        val isSuperAdmin = snapshot.getBoolean("isSuperAdmin") == true
        val isAdmin = snapshot.getBoolean("isAdmin") == true
        if (isSuperAdmin || roleRaw == "super_admin" || isAdmin || roleRaw == AppRole.ADMIN.raw) {
            return AppRole.ADMIN
        }
        val status = snapshot.getString("status")?.lowercase()
        if (!status.isNullOrBlank() && status != "active") {
            return AppRole.VIEWER
        }
        return when {
            else -> AppRole.fromRaw(roleRaw)
        }
    }


    companion object {
        internal fun resolveEffectiveRole(
            isConfiguredAdmin: Boolean,
            localRole: AppRole?,
            localUserIsActive: Boolean,
            firestoreRole: AppRole?,
            totalUsers: Int,
            hasAuthenticatedEmail: Boolean
        ): AppRole = when {
            isConfiguredAdmin -> AppRole.ADMIN
            firestoreRole != null -> firestoreRole
            localRole == AppRole.ADMIN -> AppRole.ADMIN
            localUserIsActive && localRole != null -> localRole
            totalUsers == 0 && hasAuthenticatedEmail -> AppRole.ADMIN
            else -> AppRole.fromRaw(null)
        }
    }

}
