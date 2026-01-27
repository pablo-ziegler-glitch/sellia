package com.example.selliaapp.auth

interface TenantProvider {
    fun currentTenantId(): String?
    suspend fun requireTenantId(): String
}
