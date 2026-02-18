package com.example.selliaapp.repository.impl

import com.example.selliaapp.auth.FirebaseSessionCoordinator
import com.example.selliaapp.auth.TenantProvider
import com.example.selliaapp.di.AppModule
import com.example.selliaapp.repository.TenantOwnershipRepository
import com.google.firebase.functions.FirebaseFunctions
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class TenantOwnershipRepositoryImpl @Inject constructor(
    private val functions: FirebaseFunctions,
    private val tenantProvider: TenantProvider,
    private val sessionCoordinator: FirebaseSessionCoordinator,
    @AppModule.IoDispatcher private val io: CoroutineDispatcher
) : TenantOwnershipRepository {

    override suspend fun associateOwner(targetEmail: String): Result<Unit> = invokeAction(
        action = "ASSOCIATE_OWNER",
        targetEmail = targetEmail,
        keepPreviousOwnerAccess = true
    )

    override suspend fun transferPrimaryOwner(
        targetEmail: String,
        keepPreviousOwnerAccess: Boolean
    ): Result<Unit> = invokeAction(
        action = "TRANSFER_PRIMARY_OWNER",
        targetEmail = targetEmail,
        keepPreviousOwnerAccess = keepPreviousOwnerAccess
    )

    override suspend fun delegateStore(targetEmail: String): Result<Unit> = invokeAction(
        action = "DELEGATE_STORE",
        targetEmail = targetEmail,
        keepPreviousOwnerAccess = true
    )

    private suspend fun invokeAction(
        action: String,
        targetEmail: String,
        keepPreviousOwnerAccess: Boolean
    ): Result<Unit> = withContext(io) {
        runCatching {
            sessionCoordinator.runWithFreshSession {
                val tenantId = tenantProvider.requireTenantId()
                val payload = mapOf(
                    "tenantId" to tenantId,
                    "action" to action,
                    "targetEmail" to targetEmail.trim().lowercase(),
                    "keepPreviousOwnerAccess" to keepPreviousOwnerAccess
                )
                functions
                    .getHttpsCallable("manageTenantOwnership")
                    .call(payload)
                    .await()
            }
        }.map { Unit }
    }
}
