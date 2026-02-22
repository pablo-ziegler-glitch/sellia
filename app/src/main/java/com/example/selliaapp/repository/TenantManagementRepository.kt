package com.example.selliaapp.repository

interface TenantManagementRepository {
    suspend fun requestTenantDeactivation(): Result<Unit>
    suspend fun requestTenantReactivation(): Result<Unit>
    suspend fun deleteTenantWithDoubleCheck(confirmTenantId: String, confirmPhrase: String): Result<Unit>
}
