package com.example.selliaapp.repository.impl

import com.example.selliaapp.data.dao.UserDao
import com.example.selliaapp.di.AppModule.IoDispatcher
import com.example.selliaapp.domain.security.AppRole
import com.example.selliaapp.domain.security.RolePermissions
import com.example.selliaapp.domain.security.UserAccessState
import com.example.selliaapp.repository.AccessControlRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessControlRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val auth: FirebaseAuth,
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
        val user = when {
            !email.isNullOrBlank() -> userDao.getByEmail(email)
            else -> userDao.getFirst()
        }
        val totalUsers = userDao.countUsers()
        val role = when {
            user != null && user.isActive -> AppRole.fromRaw(user.role)
            totalUsers == 0 && !email.isNullOrBlank() -> AppRole.SUPER_ADMIN
            else -> AppRole.fromRaw(null)
        }
        UserAccessState(
            email = user?.email ?: email,
            role = role,
            permissions = RolePermissions.forRole(role)
        )
    }
}
