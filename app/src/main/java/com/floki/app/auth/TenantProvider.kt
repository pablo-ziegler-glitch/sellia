package com.floki.app.auth

interface TenantProvider {
    fun currentTenantId(): String?
    suspend fun requireTenantId(): String
}
