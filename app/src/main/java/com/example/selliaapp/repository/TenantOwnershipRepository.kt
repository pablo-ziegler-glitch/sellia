package com.example.selliaapp.repository

interface TenantOwnershipRepository {
    suspend fun associateOwner(targetEmail: String): Result<Unit>
    suspend fun transferPrimaryOwner(targetEmail: String, keepPreviousOwnerAccess: Boolean): Result<Unit>
    suspend fun delegateStore(targetEmail: String): Result<Unit>
}
